# OpenAPI 3.0 with Swagger UI for versioned Spring Boot application
This article continues the story of [One way to version REST API with Spring Boot](https://github.com/mcdimus/demo-spring-api-versioning-and-swagger).
The code is in the same repo.

Having the versioned REST API we might want to have documentation showing the set of endpoints available under every version.
We know that nobody is going to keep documentation in actual state unless it is automatically generated, right?
Thus, it would be awesome to have this documentation automatically generated based on the `@RestController`s code.

There is a way to achieve this!

## What is the OpenAPI and Swagger UI?
I will quote the official [docs](https://swagger.io/docs/specification/about/):

> __OpenAPI Specification__ (formerly Swagger Specification) is an API description format for REST APIs. An OpenAPI file allows you to describe your entire API, including:
> * Available endpoints (/users) and operations on each endpoint (GET /users, POST /users)
> * Operation parameters Input and output for each operation
> * Authentication methods
> * Contact information, license, terms of use and other information.

> __Swagger UI__ – renders OpenAPI specs as interactive API documentation.

## Spring support
What is meant by Spring support? 
The library implementing the OpenAPI specification must provide an automated generation of REST API documentation in Spring application.

Many Spring developers are familiar with the OpenAPI implementation [SpringFox](http://springfox.github.io/springfox/).
But it seems that it is becoming obsolete as the latest release (2.9.2) was made on Jun 24, 2018,
which conforms to the OpenAPI 2.0 specification.
At the time of writing the latest spec version is 3.0.

Luckily, there is already a new implementation: https://springdoc.github.io/springdoc-openapi-demos/.
It provides both documentation generation and UI. According to the documentation,
it should be as easy as adding a dependency into the project.

Well, lets find out ourselves.

## Getting started
First of all, check out the `versioning-only` branch from the previous article: https://github.com/mcdimus/demo-spring-api-versioning-and-swagger.
```shell script
git clone -b versioning-only https://github.com/mcdimus/demo-spring-api-versioning-and-swagger.git
```

Add _springdoc_ dependencies into `build.gradle.kts`:
```kotlin
implementation("org.springdoc:springdoc-openapi-ui:1.2.34")
```

Run the project and navigate your browser to http://localhost:8080/swagger-ui.html.

Done!

## Customization
As we can see, it is really very easy to add automated REST API documentation and Swagger UI into the project.
We can see all the available endpoints at once. 
However, wouldn't it be nicer to be able to select a particular version, and look only at endpoints for this particular version?
Moreover, we have a special version `latest` which has no explicit mapping to controllers. 
Would be great to get a view of the latest available version on Swagger UI as well.

Additionally, we might want to add some default headers, and authorization.

As, probably, you have guessed already - there is a way to customize __springdoc__ to match all our wishes.
Let me show how. 

### Description and application version
To start with, lets add the application name, description and version.

Create `@Configuration` `OpenApiConfig` and add `OpenAPI` `@Bean` declaration:
```java
@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI applicationOpenApi() {
    return new OpenAPI()
      .info(new Info()
        .title("Demo Spring API versioning application with Swagger")
        .description(
          "Application demonstrates one way to version REST API in Spring Boot application. " +
            "Moreover, adds customized OpenAPI automatically generated documentation."
        ).version("0.0.1-SNAPSHOT")
      )
      .externalDocs(new ExternalDocumentation()
        .description("Source code is available at GitHub repository")
        .url("https://github.com/mcdimus/demo-spring-api-versioning-and-swagger")
      );
  }
}
```

Quite straightforward, isn't it? There are two things to improve:
* Description can be quite long, so let's move it into separate file in `reosurces`;
* Hardcoded version is no good. We need to automatically get the same version we have in `build.gradle.kts`.

The first one is easy. Just create a file `resources/openapi/description` and read its contents in `applicationOpenApi()`.

The second might have been challenging, but Spring Boot has it covered. 
There is [a great article](https://www.vojtechruzicka.com/spring-boot-version/) on how to get build properties.
According to the article, we need to add the following into `build.gradle.kts`:
```kotlin
springBoot {
  buildInfo()
}
```

... inject `BuildProperties` into `OpenApiConfig`:
```java
@Autowired
private BuildProperties buildInfo;
```

... and replace hardcoded version with `buildInfo.getVersion()`.

Resulting configuration might look as follows:
```java
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
```

#### Possible problem with BuildInfo
With the given configuration, `META-INF/build-info.properties` (which is required to `BuildProperties` bean to be created) will be generated only when application is built with Gradle.
If you would like to run application from an IDE, then you might see the similar error:
```
***************************
APPLICATION FAILED TO START
***************************

Description:

Field buildInfo in eu.maksimov.demo.spring.versioning.config.OpenApiConfig required a bean of type 'org.springframework.boot.info.BuildProperties' that could not be found.

The injection point has the following annotations:
	- @org.springframework.beans.factory.annotation.Autowired(required=true)

The following candidates were found but could not be injected:
	- Bean method 'buildProperties' in 'ProjectInfoAutoConfiguration' not loaded because @ConditionalOnResource did not find resource '${spring.info.build.location:classpath:META-INF/build-info.properties}'
```

The simplest solution to this is to add a dummy `META-INF/build-info.properties` into the project,
and make sure it will be overridden when project is built with Gradle.
* Create file `src/main/resources/META-INF/build-info.properties` (your values may be different):
  ```properties
  # DO NOT EDIT (will be overridden by Spring Boot plugin)
  build.artifact=dummy
  build.group=dummy
  build.name=dummy
  build.time=2020-02-11T17\:01\:09.598966Z
  build.version=dummy
  ```
* Make the `bootBuiultInfo` task to be run after the `processResources` task:
  ```kotlin
  tasks.withType<BuildInfo> {
    mustRunAfter(tasks.processResources)
  }
  ```

### Default headers
Next let's see how to add default headers. Suppose our API requires two headers in all incoming requests: _X-Application-Id_ and _X-User-Id_.
Define them as _components_ in `OpenAPI` bean.
```java
new OpenAPI()
  // ...
  .components(new Components()
    .addParameters("header-x-application-id",
      new Parameter().in(ParameterIn.HEADER.toString()).name("X-Application-Id").required(true).schema(new StringSchema()._default("swagger-ui")))
    .addParameters("header-x-user-id",
      new Parameter().in(ParameterIn.HEADER.toString()).name("X-User-Id").required(true).schema(new StringSchema()._default("swagger-ui")))
  );
```

Declare `OperationCustomizer` `@Bean`:
```java
@Bean
public OperationCustomizer operationCustomizer() {
  return (operation, handlerMethod) -> {
    List<Parameter> newParameters = new ArrayList<>();
    newParameters.add(new Parameter().$ref("#/components/parameters/header-x-application-id"));
    newParameters.add(new Parameter().$ref("#/components/parameters/header-x-user-id"));
    newParameters.addAll(operation.getParameters());

    operation.setParameters(newParameters);
    return operation;
  };
}
```

Now if you are going to test endpoint from Swagger UI,
it will show and send the corresponding header inputs pre-filled with the defined default values.

### Authorization
It is a rare REST service that does not require authorization.
Suppose, our service requires _Authorization_ header with a correct value.
One solution would be to add a default header as in the previous section. 
The difference is, that we would not be able to supply a default value. 
We rather need to get a real authorization token, insert into the field and then issue a request.
If you think it may become tedious, then yes, it will.

Fortunately there is a better solution to this as well.

Add a `SecurityScheme` definition to the `Components` declaration:
```java
new OpenAPI()
  // ...
  .components(new Components()
    // ...
    .addSecuritySchemes("access-token",
      new SecurityScheme().type(HTTP).scheme("bearer").in(SecurityScheme.In.HEADER).name("Authorization")
    )
  );
```

And a `SecurityRequirement` into `OperationCustomizer` definition:
```java
operation.addSecurityItem(new SecurityRequirement().addList("access-token"));
```

This will add green __Authorize__ button to the Swagger UI. It can be used to provide a one time authorization token,
and Swagger UI will automatically add the _Authorization_ header with the provided token to every outgoing request.
Thus, no need to provide it every time by hand.

There is more to it, you can apply the `SecurityRequrement` only to some endpoints.
Did you notice that the `OperationCustomizer` has a second parameter - `handlerMethod`.
Use it to make filtering decision. E.g. lets require authorization only for _session_ endpoints:
```java
Stream<String> paths = Arrays.stream(handlerMethod.getBeanType().getAnnotation(RequestMapping.class).value());
if (paths.allMatch(it -> it.matches("/api/v\\d{1,2}/session"))) {
  operation.addSecurityItem(new SecurityRequirement().addList("access-token"));
}
```
This case, even if authorization value was defined, Swagger UI will send _Authorization_ header only for _session_ endpoints.

__NB!__ This configuration is only for OpenAPI and Swagger UI, you still need to implement proper security in your application.

### Servers
You can notice a _Servers_ labeled dropbox.
It is possible to customize this list with `io.swagger.v3.oas.models.OpenAPI#servers`.
However, if you don't, then _spring-doc_ will provide a generated value.
It is OK in most cases. Nevertheless, if the application will be deployed behind an HTTPS proxy,
then the generated url will be incorrect.

__To fix the issue__:
* make sure that X-Forwarded headers are sent by your proxy (X-Forwarded-*).
Especially `X-Forwarded-Proto` as Apache Proxy does not send it by default;
* add `server.forward-headers-strategy=FRAMEWORK` to `application.properties`.
 
### Grouping by version
Finally, we have reached the most complex part. How to group endpoints by a version?

__Springdoc__'s documentation has an [example](https://springdoc.github.io/springdoc-openapi-demos/faq.html#how-can-i-define-multiple-openapi-definitions-in-one-spring-boot-project).
And this approach works well, but not in our case.
The problem is that some of our endpoints do not exist in the code. Bright examples are the endpoints under `latest` version.

The solution is to use the same `GroupedOpenApi` beans,
but we need to generate them programmatically instead of usual declarative approach.

As simple as it sounds, it turned out to be a rather challenging task.
Whatever I was trying, it just was not working the way I wanted.
After some digging, I have found that the problem was with [org.springdoc.core.MultipleOpenApiSupportConfiguration](https://github.com/springdoc/springdoc-openapi/blob/v1.2.34/springdoc-openapi-webmvc-core/src/main/java/org/springdoc/core/MultipleOpenApiSupportConfiguration.java),
`@Configuration` class responsible for `GroupedOpenApi` instances to be taken into OpenAPI processing.
Notice the annotation `@ConditionalOnBean(GroupedOpenApi.class)`.
It means that the given auto-configuration will be applied only when there is a `GroupedOpenApi` bean in the application context.
With all obvious ways of dynamic bean registration,
these beans were added to the context __after__ `MultipleOpenApiSupportConfiguration`'s condition annotation was evaluated.

Consequently, `GroupedOpenApi` beans registration should happen before the configuration conditions evaluation.

Configuration conditions evaluation happens in [ConfigurationClassPostProcessor](https://docs.spring.io/spring-framework/docs/5.2.4.RELEASE/javadoc-api/org/springframework/context/annotation/ConfigurationClassPostProcessor.html).
Thus, we need to register `GroupedOpenApi` beans before `ConfigurationClassPostProcessor` is executed.

For that reason, we will create another `BeanDefinitionRegistryPostProcessor`
and make it to be executed before `ConfigurationClassPostProcessor`.

Processor's code can be found in the [repo](https://github.com/mcdimus/demo-spring-api-versioning-and-swagger/blob/master/src/main/java/eu/maksimov/demo/spring/versioning/config/OpenApiGroupProcessor.java).
Here I would briefly outline the most important things.

1. Implement `PriorityOrdered` interface with `Ordered.HIGHEST_PRECEDENCE` to make sure `OpenApiGroupProcessor` is run before other processors:
   ```java
     @Override
     public int getOrder() {
       return Ordered.HIGHEST_PRECEDENCE;
     }
   ```
2. Use [ClassPathScanningCandidateComponentProvider](https://docs.spring.io/spring/docs/5.2.4.RELEASE/javadoc-api/org/springframework/context/annotation/ClassPathScanningCandidateComponentProvider.html)
to find all `RestController`s and respective endpoint paths:
   ```java
   var restControllersScanner = new ClassPathScanningCandidateComponentProvider(false);
       restControllersScanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
   ```
3. Find all available versions based on found paths. Additionally, I am introducing a notion of _scope_.
Basically, that is just the first part of a path. In our case it is always `api`.
But this way we are leaving a possibility to make grouping more flexible, if it would be necessary;
4. `registerGroupedOpenApi` constructs _springdoc_'s `GroupedOpenApi` beans and registers it with Spring context.
The important part here is the addition of `OpenApiCustomiser`.
It overrides the API version in endpoint paths in accordance with the current version group.

# Conclusion
Run application and navigate a web browser to http://localhost:8080/swagger-ui.html.
You will see API group selection dropbox in the right upper corner.
Try to switch groups and notice how endpoints, and their corresponding urls are changing.

Now your REST API, versioned in a tricky way, is fully integrated with OpenAPI and Swagger UI.
