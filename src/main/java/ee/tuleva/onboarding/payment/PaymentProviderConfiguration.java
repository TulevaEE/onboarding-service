package ee.tuleva.onboarding.payment;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("payment-provider")
public class PaymentProviderConfiguration {

  private final Map<Bank, PaymentProviderBank> banks;
  private Map<String, PaymentProviderBank> banksByBic;

  public PaymentProviderBank getPaymentProviderBank(Bank bank) {
    return banks.get(bank);
  }

  public PaymentProviderBank getPaymentProviderBank(String bic) {
    return banksByBic.get(bic);
  }

  @PostConstruct
  private void mapByBic() {
    banksByBic =
        banks.entrySet().stream().collect(toMap(entry -> entry.getValue().bic, Entry::getValue));
  }
}
