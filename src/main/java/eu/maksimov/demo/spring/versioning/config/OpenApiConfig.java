package eu.maksimov.demo.spring.versioning.config;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

@Configuration
public class OpenApiConfig {

  @Autowired
  private ResourceLoader resourceLoader;
  @Autowired
  private BuildProperties buildInfo;

  @Bean
  public OpenAPI applicationOpenApi() throws IOException {
    return new OpenAPI()
      .info(new Info()
        .title("Demo Spring API versioning application with Swagger")
        .version(buildInfo.getVersion())
        .description(
          getResourceContents("classpath:openapi/description")
            + "<hr/><p>Buildtime (UTC): " + buildInfo.getTime() + "<p/><hr/>"
        )
      )
      .externalDocs(new ExternalDocumentation()
        .description("GitHub repository")
        .url("https://github.com/mcdimus/demo-spring-api-versioning-and-swagger")
      )
      .components(new Components()
        .addParameters("header-x-application-id",
          new Parameter().in(ParameterIn.HEADER.toString()).name("X-Application-Id").required(true).schema(new StringSchema()._default("swagger-ui"))
        ).addParameters("header-x-user-id",
          new Parameter().in(ParameterIn.HEADER.toString()).name("X-User-Id").required(true).schema(new StringSchema()._default("swagger-ui"))
        ).addSecuritySchemes("access-token",
          new SecurityScheme().type(HTTP).scheme("bearer").in(SecurityScheme.In.HEADER).name("Authorization")
        )
      );
  }

  private String getResourceContents(String location) throws IOException {
    var resource = resourceLoader.getResource(location);
    try (
      var in = new InputStreamReader(resource.getInputStream());
      var reader = new BufferedReader(in)
    ) {
      return reader.lines().collect(joining("\n"));
    }
  }

  @Bean
  public OperationCustomizer operationCustomizer() {
    return (operation, handlerMethod) -> {
      List<Parameter> newParameters = new ArrayList<>();
      newParameters.add(new Parameter().$ref("#/components/parameters/header-x-application-id"));
      newParameters.add(new Parameter().$ref("#/components/parameters/header-x-user-id"));
      newParameters.addAll(operation.getParameters() == null ? emptyList() : operation.getParameters());
      operation.setParameters(newParameters);

      Stream<String> paths = Arrays.stream(handlerMethod.getBeanType().getAnnotation(RequestMapping.class).value());
      if (paths.allMatch(it -> it.matches("/api/v\\d{1,2}/session"))) {
        operation.addSecurityItem(new SecurityRequirement().addList("access-token"));
      }

      return operation;
    };
  }

}
