package ee.tuleva.onboarding.investment.transaction;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin")
public record AdminTokenProperties(String apiToken, Map<String, String> operatorTokens) {

  public AdminTokenProperties {
    apiToken = apiToken == null ? "" : apiToken;
    operatorTokens = operatorTokens == null ? Map.of() : Map.copyOf(operatorTokens);
    operatorTokens.forEach(
        (operator, token) -> {
          if (token.isBlank()) {
            throw new IllegalStateException(
                "Admin operator token cannot be blank: operator=" + operator);
          }
        });
  }
}
