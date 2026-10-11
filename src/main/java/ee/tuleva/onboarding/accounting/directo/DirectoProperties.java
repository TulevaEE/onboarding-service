package ee.tuleva.onboarding.accounting.directo;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "directo")
record DirectoProperties(
    String url,
    @DefaultValue Map<String, String> apiKeys,
    @DefaultValue("1s") Duration retryDelay) {

  List<String> entities() {
    return apiKeys.keySet().stream().sorted().toList();
  }

  String apiKey(String entity) {
    var apiKey = apiKeys.get(entity);
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "Directo API key missing: entity="
              + entity
              + ", environmentVariable="
              + environmentVariable(entity));
    }
    return apiKey;
  }

  List<String> missingKeyEnvironmentVariables() {
    return apiKeys.entrySet().stream()
        .filter(entry -> entry.getValue().isBlank())
        .map(entry -> environmentVariable(entry.getKey()))
        .sorted()
        .toList();
  }

  private static String environmentVariable(String entity) {
    return "DIRECTO_API_KEY_" + entity;
  }
}
