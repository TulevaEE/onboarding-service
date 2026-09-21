package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment-report-publishing.wordpress")
record WordPressProperties(
    @Nullable String apiBase, @Nullable String username, @Nullable String appPassword) {

  private record Property(String name, @Nullable String value) {

    boolean isMissing() {
      return value == null || value.isBlank();
    }
  }

  List<String> missingPropertyNames() {
    return Stream.of(
            new Property("api-base (WP_API_BASE)", apiBase),
            new Property("username (WP_USERNAME)", username),
            new Property("app-password (WP_APP_PASSWORD)", appPassword))
        .filter(Property::isMissing)
        .map(Property::name)
        .toList();
  }
}
