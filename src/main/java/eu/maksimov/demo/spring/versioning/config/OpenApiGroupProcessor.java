package eu.maksimov.demo.spring.versioning.config;

import eu.maksimov.demo.spring.versioning.Application;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Needs to be executed before [org.springframework.context.annotation.ConfigurationClassPostProcessor].
 */
public class OpenApiGroupProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiGroupProcessor.class);
  public static final Marker MARKER = MarkerFactory.getMarker("[OpenAPI]");

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    var startedTime = System.currentTimeMillis();
    LOG.debug(MARKER, "{}s dynamic registration", GroupedOpenApi.class.getSimpleName());

    var endpointPaths = getEndpointPaths();
    LOG.debug(MARKER, "Found endpoints:");
    endpointPaths.forEach(it -> LOG.debug(MARKER, it));

    var versionsPerScope = getVersionsPerScope(endpointPaths);

    versionsPerScope.forEach((scope, versions) -> versions.forEach(
      version -> registerGroupedOpenApi(registry, scope, version)
    ));
    LOG.debug(MARKER, "Finished in {} ms", (System.currentTimeMillis() - startedTime));
  }

  private Set<String> getEndpointPaths() {
    var restControllersScanner = new ClassPathScanningCandidateComponentProvider(false);
    restControllersScanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

    return restControllersScanner.findCandidateComponents(Application.class.getPackageName()).stream()
      .filter(it -> it instanceof ScannedGenericBeanDefinition)
      .flatMap(it -> getRequestMappingPaths((ScannedGenericBeanDefinition) it).stream())
      .collect(toSet());
  }

  private List<String> getRequestMappingPaths(ScannedGenericBeanDefinition restControllerBean) {
    var annotations = restControllerBean.getMetadata().getAnnotations();
    var requestMappingAnnotation = annotations.get(RequestMapping.class);

    if (!requestMappingAnnotation.isPresent()) {
      throw new IllegalStateException("RestController '"
        + restControllerBean.getBeanClassName()
        + "' does not have RequestMapping annotation"
      );
    }

    return asList(requestMappingAnnotation.getStringArray("value"));
  }

  private Map<String, Set<String>> getVersionsPerScope(Set<String> endpointPaths) {
    Map<String, Set<String>> versionsPerScope = endpointPaths.stream()
      .map(path -> Arrays.stream(path.split("/")).filter(it -> !it.isBlank()).limit(2).toArray(String[]::new))
      .collect(groupingBy(it -> it[0], mapping(it -> it[1], toSet())));

    versionsPerScope.forEach((scope, versions) -> versions.add("latest"));

    return versionsPerScope;
  }

  private void registerGroupedOpenApi(BeanDefinitionRegistry registry, String scope, String version) {
    var groupName = scope + "-" + version;
    LOG.debug(MARKER, "Registering {} '{}'", GroupedOpenApi.class.getSimpleName(), groupName);

    Supplier<GroupedOpenApi> groupedApiSupplier = () -> GroupedOpenApi.builder()
      .setGroup(groupName)
      .pathsToMatch("/" + scope + "/**")
      .addOpenApiCustomiser(api -> customizePaths(api, version))
      .build();

    var bean = BeanDefinitionBuilder.genericBeanDefinition(GroupedOpenApi.class, groupedApiSupplier)
      .getBeanDefinition();
    registry.registerBeanDefinition(scope + "-" + version + "-groupedOpenApi", bean);
  }

  private void customizePaths(OpenAPI api, String version) {
    var pathItemsPerVersion = api.getPaths().entrySet().stream()
      .collect(groupingBy(it -> getApiVersion(it.getKey()), TreeMap::new, toList()));

    var newPaths = new Paths();
    pathItemsPerVersion.entrySet().stream()
      .filter(it -> version.equals("latest") || it.getKey() <= Integer.parseInt(version.substring(1)))
      .forEach(pathItemPerVersion -> {
        for (var entry : pathItemPerVersion.getValue()) {
          newPaths.addPathItem(entry.getKey().replace("/v" + pathItemPerVersion.getKey() + "/", "/" + version + "/"), entry.getValue());
        }
      });

    api.setPaths(newPaths);
  }

  private int getApiVersion(String path) {
    return Arrays.stream(path.split("/")).filter(it -> !it.isBlank())
      .skip(1).limit(1)
      .map(it -> it.substring(1)) // trimStart 'v'
      .map(Integer::parseInt)
      .findFirst().orElseThrow();
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // no action
  }

}
