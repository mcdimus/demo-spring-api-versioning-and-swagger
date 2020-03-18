package eu.maksimov.demo.spring.versioning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @see <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#mvc-config-path-matching">Path Matching</a>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer
      .setPathMatcher(versionedAntPathMatcher());
//      .addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
  }

  @Bean
  public PathMatcher versionedAntPathMatcher() {
    return new VersionedAntPathMatcher();
  }
}
