# One way to version REST API with Spring Boot
Do you version your API? Well, you definitely should. 
I believe it is so obvious there is no need to bring any examples. Am I right?

What options do we have to version an API? If one will start googling the topic, he/she will find three approaches:
* a URL, e.g. `https://example.com/api/v1/person/{id}`
* a custom header, e.g. `Accept-Version: v1`
* a custom media type, e.g. `Accept: application/vnd.example.v1+json`

There are pros and cons for every approach. Nevertheless, experience shows that the first one, URL, is the easiest to follow and conform.
We are going to focus on API versioning using version identification in URL.

# Advantages and Disadvantages
The main advantage is the clarity. User of API clearly defines which version is to be used. 
It is easy to consume `GET` endpoint of such API and easy to use client caching.

The disadvantage is that there is no possibility to evolve parts of the API. 
Suppose, we have REST service with endpoints and corresponding `@RestController`s:
```
/api/v1/person/{id} --> PersonControllerV1
/api/v1/session     --> SessionControllerV1
```
So far, so good. Now, we have decided to change output of `/api/v1/session` in a non backward-compatible way.
Obviously we need to increment version:
```
/api/v1/person/{id} --> PersonControllerV1.getById()
/api/v2/session     --> SessionControllerV2.getAll()
/api/v1/session     --> SessionControllerV1.getAll()
```
After the introduction of the second version of `SessionController`,
API has a problem as `/api/v2/person/{id}` is not available. 
Thus, we are forced to make the second version of `PersonController` as well.
Although Spring allows to avoid code duplication by specifying several paths into `@RequestMapping`:
```java
@RestController
@RequestMapping({"/api/v1/person","/api/v2/person"})
public class PersonV1Controller { 
  // ... 
}
```
, you are still forced to do it manually (and what happens when you accidentally forget to update some controller?).

With the `SessionController` you need to choose how these two versions are going to be related.
Inheritance? Composition? Or just copy-paste and therefore code duplication? 

There should be a better solution...

# Goal
The idea of what we would like to achieve is quite simple. We want to be able to evolve our API partially and without code duplication.
I will explain it on a concrete example.

Suppose we have the following controllers:
```java
@RestController
@RequestMapping("/api/v1/person")
public class PersonV1Controller { 
  @GetMapping public List<Person> getAll() { /* ... */ }
  @GetMapping("{id}") public Person getById(@PathVariable String id) { /* ... */ }
}

@RestController
@RequestMapping("/api/v1/session")
public class SessionV1Controller { 
  @GetMapping public List<Session> getAll() { /* ... */ }
  @GetMapping("{id}") public Session getById(@PathVariable String id) { /* ... */ }
}
```
So URIs are:
```
/api/v1/person
/api/v1/person/{id}
/api/v1/session
/api/v1/session/{id}
```

The other day we are deciding to add a new version of getting a `Person` by id:
```java
@RestController
@RequestMapping("/api/v2/person")
public class PersonV2Controller { 
  @GetMapping("{id}") public Person getById(@PathVariable String id) { /* ... */ }
}
```
We are fine with the `PersonV1Controller.getAll` method, and would like to leave it unchanged.
So here we would like that the second method would be available under `/api/v2/person` without us doing anything extra.
Same with the sessions. We would like the same method to be available under `/api/v2/session`, again without us lifting a finger.

Additionally, we might want to have a possibility for API clients to have access to the latest version all the time.
```
/api/latest/person
/api/latest/person/{id}
/api/latest/session
/api/latest/session/{id}
```

Having the mentioned `RestController`s, the mapping rules would be as follows:
* `/api/v1/person` maps to `PersonV1Controller.getAll`
* `/api/v1/person/{id}` maps to `PersonV1Controller.getById`
* `/api/v2/person` maps to `PersonV1Controller.getAll`
* `/api/v2/person/{id}` maps to `PersonV2Controller.getById`
* `/api/vX/person` maps to `PersonV1Controller.getAll`
* `/api/vX/person/{id}` maps to `PersonV2Controller.getById`
* `/api/latest/person` maps to `PersonV1Controller.getAll`
* `/api/latest/person/{id}` maps to `PersonV2Controller.getById`
* `/api/v1/session` maps to `SessionV1Controller.getAll`
* `/api/v1/session/{id}` maps to `SessionV1Controller.getById`
* `/api/v2/session` maps to `SessionV1Controller.getAll`
* `/api/v2/session/{id}` maps to `SessionV1Controller.getById`
* `/api/vX/session` maps to `SessionV1Controller.getAll`
* `/api/vX/session/{id}` maps to `SessionV1Controller.getById`
* `/api/latest/session` maps to `SessionV1Controller.getAll`
* `/api/latest/session/{id}` maps to `SessionV1Controller.getById`

where `X` is any number greater than the greatest version available throughout the API.

This will allow API to evolve without any code duplication.

__But be cautious__, there is a very serious drawback to this approach.
API developers should clearly understand how API should evolve. 
As soon as `PersonV1Controller`, `PersonV2Controller`, and `SessionV1Controller` would be released,
it should not be allowed to create `SessionV2Controller` mapped to `/api/v2/session`.
Here is why:
* All requests to `/api/v2/session` are handled by `SessionV1Controller`, right?
* When we create `SessionV2Controller`, then it will handle `/api/v2/session` instead of `SessionV1Controller`.
* Release of `SessionV2Controller` will most likely introduce the breaking change to the _session_ api.
* So now clients who were already using `/api/v2/session` will suddenly find that application is not working as expected anymore.

Therefore, we need to very carefully select what is the next version of our api. 
In a given scenario, `SessionV2Controller` should be skipped, and `SessionV3Controller` should be created instead.

__So keep this in mind__.

That being said, lets see how the described logic may be implemented in Spring Boot application.

# Spring and Request Mapping
Spring should have some mechanism how it is mapping actual requests to our controller methods.
A bit of reverse engineering, and we can find the following chain of method calls on an incoming HTTP request:
```
match:194, AntPathMatcher (org.springframework.util)
getMatchingPattern:271, PatternsRequestCondition (org.springframework.web.servlet.mvc.condition)
getMatchingPatterns:236, PatternsRequestCondition (org.springframework.web.servlet.mvc.condition)
getMatchingCondition:221, PatternsRequestCondition (org.springframework.web.servlet.mvc.condition)
getMatchingCondition:240, RequestMappingInfo (org.springframework.web.servlet.mvc.method)
getMatchingMapping:94, RequestMappingInfoHandlerMapping (org.springframework.web.servlet.mvc.method)
getMatchingMapping:58, RequestMappingInfoHandlerMapping (org.springframework.web.servlet.mvc.method)
addMatchingMappings:427, AbstractHandlerMethodMapping (org.springframework.web.servlet.handler)
lookupHandlerMethod:393, AbstractHandlerMethodMapping (org.springframework.web.servlet.handler)
getHandlerInternal:367, AbstractHandlerMethodMapping (org.springframework.web.servlet.handler)
getHandlerInternal:71, AbstractHandlerMethodMapping (org.springframework.web.servlet.handler)
getHandler:395, AbstractHandlerMapping (org.springframework.web.servlet.handler)
getHandler:1234, DispatcherServlet (org.springframework.web.servlet)
doDispatch:1016, DispatcherServlet (org.springframework.web.servlet)
doService:943, DispatcherServlet (org.springframework.web.servlet)
processRequest:1006, FrameworkServlet (org.springframework.web.servlet)
doGet:898, FrameworkServlet (org.springframework.web.servlet)
...
```

As we can see, the class [AntPathMatcher](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html) is of a particular interest.

`AntPathMatcher` implements [PathMatcher](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/PathMatcher.html).
It is clear now, that we need to implement our own `PathMatcher`. We want features offered by `AntiPathMatcher` to remain though.
So let's just extend the `AntPathMatcher` and modify its behavior.

# AntPathMatcher extension
Create new class `VersionedAntPathMatcher` and set it to extend `AntPathMatcher`.
```java
public class VersionedAntPathMatcher extends AntPathMatcher {}
```

## Tests
Before we start modifying the existing functionality, we need to make sure, that our modifications will not break anything.
Copy `AntPathMatcher` tests into our project: [AntPathMatcherTests.java](https://github.com/spring-projects/spring-framework/blob/master/spring-core/src/test/java/org/springframework/util/AntPathMatcherTests.java).
Unfortunately, there are some tests for caching functionality that we need to delete as `AntPathMatcher`'s field `stringMatcherCache` has package-private access modifier.

Supply instance of `VersionedAntPathMatcher` instead of `AntPathMatcher` and verify that all tests are green.

Let's add tests for our desired mapping rules.
```java
@Test
void match_versioned() {
    assertThat(pathMatcher.match("/v1/person", "/v1/person")).isTrue();
    assertThat(pathMatcher.match("/v1/person", "/v2/person")).isTrue();
    assertThat(pathMatcher.match("/v1/person", "/latest/person")).isTrue();
    assertThat(pathMatcher.match("/v1/person/{id}", "/latest/person/1234")).isTrue();
    assertThat(pathMatcher.match("/v1/person/t?st", "/latest/person/test")).isTrue();
    assertThat(pathMatcher.match("/v1/person/t*", "/latest/person/test")).isTrue();
    assertThat(pathMatcher.match("/v1/person", "/v0/person")).isFalse();
    assertThat(pathMatcher.match("/v3/person", "/v2/person")).isFalse();
}
```

## Implementation
All heavy lifting is done in `AntPathMatcher`'s [doMatch](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html#doMatch-java.lang.String-java.lang.String-boolean-java.util.Map-) method.
Method's implementation is quite complex. We definitely don't want to mess with it. Thus, we are going to reuse at all cases.

Let's override method `doMatch` method:
```java
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionedAntPathMatcher extends AntPathMatcher {

  private static final Pattern VERSIONED_PATH_REGEX = Pattern.compile("/(?<version>v\\d{1,2}|latest)/.*");

  @Override
  protected boolean doMatch(String pattern, String path, boolean fullMatch, Map<String, String> uriTemplateVariables) {
    if (path == null) {
      return super.doMatch(pattern, path, fullMatch, uriTemplateVariables);
    }

    Matcher patternMatcher = VERSIONED_PATH_REGEX.matcher(pattern);
    Matcher pathMatcher = VERSIONED_PATH_REGEX.matcher(path);
    if (!patternMatcher.find() || !pathMatcher.find()) {
      return super.doMatch(pattern, path, fullMatch, uriTemplateVariables);
    }
    return getApiVersion(pathMatcher) >= getApiVersion(patternMatcher)
        && super.match(wildcardVersionApi(patternMatcher), path);
  }

  private int getApiVersion(Matcher pathMatcher) {
    Optional<String> versionStr = Optional.ofNullable(pathMatcher.group("version"));

    if (versionStr.isEmpty()) {
      return Integer.MIN_VALUE;
    }
    if (versionStr.get().equals("latest")) {
      return Integer.MAX_VALUE;
    }
    return Integer.parseInt(versionStr.map(it -> it.substring(1)).get());
  }

  private String wildcardVersionApi(Matcher pattern) {
    String versionStr = pattern.group("version");
    return pattern.group().replaceFirst(versionStr, "*");
  }

}
```

The central element is the regular expression `VERSIONED_PATH_REGEX`. 
If both pattern and path do not correspond to the versioned expression, then we simply delegate to `AntPathMatcher`'s `doMatch`.
Otherwise, we replace a version in the pattern with the wildcard character `*` and then delegate to `AntPathMatcher`'s `doMatch`.

Rerun tests to verify if everything is green.

Replacing a version in the pattern with the wildcard character `*` makes Spring to find all versions for the given endpoint.
But how will Spring decide which is to be actually used? 

Spring will get all matched endpoints,
sort them using the comparator returned by [AntPathMatcher.getPatternComparator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html#getPatternComparator-java.lang.String-),
and take the first one.
Here `org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#lookupHandlerMethod`:
```java
Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
matches.sort(comparator);
Match bestMatch = matches.get(0);
```

So let's add tests to verify that our versioning logic works fine with comparator as well.
```java
@Test
  void patternComparator_versioned() {
    Comparator<String> comparator = pathMatcher.getPatternComparator("/api/v2/person");

    assertThat(comparator.compare("/api/v1/person", "/api/v1/person")).isEqualTo(0);
    assertThat(comparator.compare("/api/v1/person", "/api/v2/person")).isGreaterThan(0);
    assertThat(comparator.compare("/api/v1/person", "/api/latest/person")).isGreaterThan(0);
    assertThat(comparator.compare("/api/v1/person/{id}", "/api/latest/person/1234")).isGreaterThan(0);
    assertThat(comparator.compare("/api/v1/person/t?st", "/api/latest/person/test")).isGreaterThan(0);
    assertThat(comparator.compare("/api/v1/person/t*", "/api/latest/person/test")).isGreaterThan(0);
    assertThat(comparator.compare("/api/v1/person", "/api/v0/person")).isLessThan(0);
    assertThat(comparator.compare("/api/v3/person", "/api/v2/person")).isGreaterThan(0);
  }

  @Test
  void patternComparatorSort_versioned_IfExactMatch() {
    Comparator<String> comparator = pathMatcher.getPatternComparator("/api/v2/hotels/new");
    List<String> paths = new ArrayList<>();

    paths.add("/api/v1/hotels/new");
    paths.add("/api/v2/hotels/new");
    paths.add("/api/v2/hotels/{new}");
    paths.add("/api/latest/hotels/new");
    paths.add("/api/v0/hotels/new");
    paths.add("/api/v3/hotels/new");
    Collections.sort(paths, comparator);
    assertThat(paths).containsExactly(
      "/api/v2/hotels/new",
      "/api/v2/hotels/{new}",
      "/api/latest/hotels/new",
      "/api/v3/hotels/new",
      "/api/v1/hotels/new",
      "/api/v0/hotels/new"
    );
  }

  @Test
  void patternComparatorSort_versioned_IfLatest() {
    Comparator<String> comparator = pathMatcher.getPatternComparator("/api/latest/hotels/new");
    List<String> paths = new ArrayList<>();

    paths.add("/api/v1/hotels/n*");
    paths.add("/api/v1/hotels/new");
    paths.add("/api/v2/hotels/new");
    paths.add("/api/v2/hotels/n?w");
    paths.add("/api/v0/hotels/new");
    paths.add("/api/v3/hotels/new");
    paths.add("/api/v3/hotels/{new}");
    Collections.sort(paths, comparator);
    assertThat(paths).containsExactly(
      "/api/v3/hotels/new",
      "/api/v3/hotels/{new}",
      "/api/v2/hotels/new",
      "/api/v2/hotels/n?w",
      "/api/v1/hotels/new",
      "/api/v1/hotels/n*",
      "/api/v0/hotels/new"
    );
  }

  @Test
  void patternComparatorSort_versioned_IfFutureVersion() {
    Comparator<String> comparator = pathMatcher.getPatternComparator("/api/v10/hotels/new");
    List<String> paths = new ArrayList<>();

    paths.add("/api/v1/hotels/new");
    paths.add("/api/v2/hotels/new");
    paths.add("/api/v0/hotels/new");
    paths.add("/api/v3/hotels/new");
    Collections.sort(paths, comparator);
    assertThat(paths).containsExactly(
      "/api/v3/hotels/new",
      "/api/v2/hotels/new",
      "/api/v1/hotels/new",
      "/api/v0/hotels/new"
    );
  }
```

And in the implementation we would like to sort according to version and then sort every version group with the default `AntPathMatcher` comparator.
The tricky part is that if we have three versions of api (v1, v2, v3) and request is coming for version 2, then _v2_ paths should be preferable over others.

```java
@Override
public Comparator<String> getPatternComparator(String path) {
  return ((Comparator<String>) (pattern1, pattern2) -> {
    if (pattern1 == null || pattern2 == null) {
      return 0;
    }

    Matcher pathMatcher = VERSIONED_PATH_REGEX.matcher(path);
    Matcher pattern1Matcher = VERSIONED_PATH_REGEX.matcher(pattern1);
    Matcher pattern2Matcher = VERSIONED_PATH_REGEX.matcher(pattern2);

    if (pathMatcher.matches() && pattern1Matcher.matches() && pattern2Matcher.matches()) {
      int pathVersion = getApiVersion(pathMatcher);
      int pattern1Version = getApiVersion(pattern1Matcher);
      int pattern2Version = getApiVersion(pattern2Matcher);

      boolean pattern1EqualsPath = pattern1Version == pathVersion;
      boolean pattern2EqualsPath = pattern2Version == pathVersion;
      if (pattern1EqualsPath && pattern2EqualsPath) {
        return 0;
      } else if (pattern1EqualsPath) {
        return -1;
      } else if (pattern2EqualsPath) {
        return 1;
      }
      return pattern2Version - pattern1Version;
    }
    return 0;
  }).thenComparing(super.getPatternComparator(path));
}
```

Run tests once more. Very important that the Spring default behaviour would be the same as it was.

## Usage
The last thing to do is to tell Spring to use our `PathMatcher` instead of a default one.
We can adapt [an example from documentation](https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#mvc-config-path-matching):
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.setPathMatcher(versionedAntPathMatcher());
  }
  
  @Bean
  public PathMatcher versionedAntPathMatcher() {
    return new VersionedAntPathMatcher();
  }
}
```

# Conclusion
We have implemented yet another way of API versioning. Lets overview its pros and cons.

__Pros__
* Explicitness
* Minimal code duplication
* No need to define "extra" versions in controllers. We are defining only one version per controller.
* Works well with service registries as we are always versioning the whole API. 

__Cons__
* One cannot release a version of a controller if other controller with same version was already released.
If there `PersonV2Controller` and `SessionV1Controller` were released, then next versions of a session controller sohuld be V3.
That is not that obvious and may cause problems if overseen.
* Code may become hard to follow with many releases. If there are 10 versions of `PersonController`
with each adding new method (thus, every controller has exactly one method), then from the code point of view it is not obvious that the version 10 of `Person` API has 10 methods (not one as in Controller).

That being said, the given way of API versioning is definitely not the ideal one, nevertheless it might have its use cases.
