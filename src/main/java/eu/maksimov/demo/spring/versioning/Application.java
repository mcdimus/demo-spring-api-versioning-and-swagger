package eu.maksimov.demo.spring.versioning;

import eu.maksimov.demo.spring.versioning.config.OpenApiGroupProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    Class<?>[] primarySources = {Application.class, OpenApiGroupProcessor.class};
    SpringApplication.run(primarySources, args);
  }

}
