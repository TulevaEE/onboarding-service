package ee.tuleva.onboarding.investment.transaction.export;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transaction-registry.custodian-order-email")
public record CustodianOrderEmailProperties(boolean enabled, List<String> to, List<String> cc) {

  public CustodianOrderEmailProperties {
    to = to == null ? List.of() : List.copyOf(to);
    cc = cc == null ? List.of() : List.copyOf(cc);
  }

  boolean isSendable() {
    return enabled && !to.isEmpty();
  }
}
