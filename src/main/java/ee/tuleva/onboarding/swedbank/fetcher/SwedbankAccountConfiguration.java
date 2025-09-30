package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "swedbank-gateway")
@Setter
@Getter
public class SwedbankAccountConfiguration {

  private Map<String, String> accounts = new HashMap<>();

  public Optional<String> getAccountIban(SwedbankAccount account) {
    return Optional.ofNullable(accounts.getOrDefault(account.getConfigurationKey(), null));
  }
}
