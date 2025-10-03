package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount;

import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsFundAccountIdentifier {

  private final Map<String, BankAccountType> ibanToAccountTypeMap;

  public Optional<BankAccountType> identifyAccountType(String iban) {
    return Optional.ofNullable(ibanToAccountTypeMap.get(iban));
  }

  public boolean isAccountType(String iban, BankAccountType type) {
    return identifyAccountType(iban).map(accountType -> accountType == type).orElse(false);
  }
}

@Configuration
class SavingsFundAccountIdentifierConfiguration {

  @Bean
  Map<String, BankAccountType> ibanToAccountTypeMap(
      SwedbankAccountConfiguration swedbankAccountConfiguration) {
    return Arrays.stream(SwedbankAccount.values())
        .filter(account -> swedbankAccountConfiguration.getAccountIban(account).isPresent())
        .collect(
            Collectors.toMap(
                account -> swedbankAccountConfiguration.getAccountIban(account).get(),
                SavingsFundAccountIdentifierConfiguration::toBankAccountType));
  }

  private static BankAccountType toBankAccountType(SwedbankAccount account) {
    return switch (account) {
      case DEPOSIT_EUR -> BankAccountType.DEPOSIT_EUR;
      case WITHDRAWAL_EUR -> BankAccountType.WITHDRAWAL_EUR;
      case INVESTMENT_EUR -> BankAccountType.FUND_INVESTMENT_EUR;
    };
  }
}
