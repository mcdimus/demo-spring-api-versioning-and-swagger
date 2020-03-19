package eu.maksimov.demo.spring.versioning.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

}
