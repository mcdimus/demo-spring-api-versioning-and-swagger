package eu.maksimov.demo.spring.versioning.config;

import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionedAntPathMatcher extends AntPathMatcher {

  private static final Pattern VERSIONED_PATH_REGEX = Pattern.compile("/api/(?<version>v\\d{1,2}|latest)/.*");

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
      && super.doMatch(wildcardVersionApi(patternMatcher), path, fullMatch, uriTemplateVariables);
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

  /**
   * Given a full path, returns a {@link Comparator} suitable for sorting patterns in order versions and then in order of
   * explicitness.
   * <p>This {@code Comparator} will {@linkplain java.util.List#sort(Comparator) sort}
   * a list so that more specific patterns (without URI templates or wild cards) come before
   * generic patterns. So given a list with the following patterns, the returned comparator
   * will sort this list so that the order will be as indicated.
   * <ol>
   * <li>{@code /api/v1/hotels/new}</li>
   * <li>{@code /api/v1/hotels/{hotel}}</li>
   * <li>{@code /api/v1/hotels/*}</li>
   * </ol>
   * <p>The full path given as parameter is used to test for versions and exact matches. So when the given path
   * is {@code /api/v2/hotels/2}, the list will be sorted as follows:
   * <ol>
   * <li>{@code /api/v2/hotels/new}</li>
   * <li>{@code /api/v2/hotels/{hotel}}</li>
   * <li>{@code /api/v2/hotels/*}</li>
   * <li>{@code /api/v3/hotels/new}</li>
   * <li>{@code /api/v3/hotels/{hotel}}</li>
   * <li>{@code /api/v3/hotels/*}</li>
   * <li>{@code /api/v1/hotels/new}</li>
   * <li>{@code /api/v1/hotels/{hotel}}</li>
   * <li>{@code /api/v1/hotels/*}</li>
   * </ol>
   * @param path the full path to use for comparison
   * @return a comparator capable of sorting patterns in order of explicitness
   */
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

}
