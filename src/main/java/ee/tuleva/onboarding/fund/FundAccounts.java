package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fund-accounts")
public class FundAccounts {

  @Setter private Map<TulevaFund, Account> funds = new EnumMap<>(TulevaFund.class);

  public record Account(String cashAccount, String securitiesAccount, String gatewayClientId) {}

  public String cashAccount(TulevaFund fund) {
    return forFund(fund).cashAccount();
  }

  public String securitiesAccount(TulevaFund fund) {
    return forFund(fund).securitiesAccount();
  }

  public String gatewayClientId(TulevaFund fund) {
    return forFund(fund).gatewayClientId();
  }

  private Account forFund(TulevaFund fund) {
    var account = funds.get(fund);
    if (account == null) {
      throw new IllegalStateException("Fund accounts not configured: fund=%s".formatted(fund));
    }
    return account;
  }

  @PostConstruct
  void validate() {
    for (TulevaFund fund : TulevaFund.values()) {
      var account = forFund(fund);
      if (!IbanValidator.isValid(account.cashAccount())) {
        throw new IllegalStateException("Invalid fund cash account: fund=%s".formatted(fund));
      }
      if (account.securitiesAccount() == null || account.securitiesAccount().isBlank()) {
        throw new IllegalStateException("Missing fund securities account: fund=%s".formatted(fund));
      }
      if (account.gatewayClientId() == null || account.gatewayClientId().isBlank()) {
        throw new IllegalStateException("Missing fund gateway client id: fund=%s".formatted(fund));
      }
    }
  }
}
