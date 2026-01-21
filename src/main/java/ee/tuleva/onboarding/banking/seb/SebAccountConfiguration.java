package ee.tuleva.onboarding.banking.seb;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.banking.BankAccountConfiguration;
import ee.tuleva.onboarding.banking.BankAccountType;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("seb-gateway")
@ConditionalOnProperty(prefix = "seb-gateway", name = "enabled", havingValue = "true")
public class SebAccountConfiguration implements BankAccountConfiguration {

  @Getter private final Map<BankAccountType, String> accounts;
  private Map<String, BankAccountType> accountsByIban;

  @Override
  @NotNull
  public String getAccountIban(BankAccountType account) {
    return Optional.ofNullable(accounts.get(account))
        .orElseThrow(
            () -> new IllegalStateException("No iban found for account=%s".formatted(account)));
  }

  @Override
  @Nullable
  public BankAccountType getAccountType(String iban) {
    return accountsByIban.get(iban);
  }

  @PostConstruct
  void mapByIban() {
    accountsByIban = accounts.entrySet().stream().collect(toMap(Entry::getValue, Entry::getKey));
  }
}
