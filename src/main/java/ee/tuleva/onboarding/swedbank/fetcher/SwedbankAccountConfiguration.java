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
@ConfigurationProperties(prefix = "swedbank-gateway.accounts")
@Setter
@Getter
public class SwedbankAccountConfiguration {

  private Map<String, String> accountIbans = new HashMap<>();

  public Optional<String> getAccountIban(SwedbankAccount account) {
    return Optional.ofNullable(accountIbans.getOrDefault(account.getConfigurationKey(), null));
  }
}
