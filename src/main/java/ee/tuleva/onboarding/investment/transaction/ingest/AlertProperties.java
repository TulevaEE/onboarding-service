package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transaction-registry.alerts")
public record AlertProperties(List<String> to, List<String> cc) {

  public AlertProperties {
    if (to == null || to.isEmpty()) {
      throw new IllegalStateException(
          "Transaction registry alerts misconfigured: property=transaction-registry.alerts.to,"
              + " value=empty — at least one recipient is required");
    }
    to = List.copyOf(to);
    cc = cc == null ? List.of() : List.copyOf(cc);
  }
}
