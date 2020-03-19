package eu.maksimov.demo.spring.versioning.config;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
          new Parameter().in(ParameterIn.HEADER.toString()).name("X-Application-Id").required(true).schema(new StringSchema()._default("swagger-ui")))
        .addParameters("header-x-user-id",
          new Parameter().in(ParameterIn.HEADER.toString()).name("X-User-Id").required(true).schema(new StringSchema()._default("swagger-ui")))
      );
  }

  private String getResourceContents(String location) throws IOException {
    Resource resource = resourceLoader.getResource(location);
    try (
      InputStreamReader in = new InputStreamReader(resource.getInputStream());
      BufferedReader reader = new BufferedReader(in)
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
      return operation;
    };
  }

}
