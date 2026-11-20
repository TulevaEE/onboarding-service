package ee.tuleva.onboarding.investment.transaction;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin")
public record AdminTokenProperties(
    String apiToken, String opsToken, Map<String, String> operatorTokens) {

  public AdminTokenProperties {
    apiToken = apiToken == null ? "" : apiToken;
    opsToken = opsToken == null ? "" : opsToken;
    operatorTokens = operatorTokens == null ? Map.of() : Map.copyOf(operatorTokens);
    requireOneTokenPerOperator(apiToken, opsToken, operatorTokens);
  }

  private static void requireOneTokenPerOperator(
      String apiToken, String opsToken, Map<String, String> operatorTokens) {
    operatorTokens.forEach(
        (operator, token) -> {
          if (token.isBlank()) {
            throw new IllegalStateException(
                "Admin operator token cannot be blank: operator=" + operator);
          }
          if (token.equals(apiToken) || token.equals(opsToken)) {
            throw new IllegalStateException(
                "Admin operator token cannot be a shared token: operator=" + operator);
          }
          if (AdminTokenAuthenticator.SHARED_TOKEN_ACTOR.equals(operator)) {
            throw new IllegalStateException(
                "Admin operator cannot take the shared token's actor name: operator=" + operator);
          }
        });
    if (Set.copyOf(operatorTokens.values()).size() < operatorTokens.size()) {
      throw new IllegalStateException("Admin operator tokens must differ between operators");
    }
  }
}
