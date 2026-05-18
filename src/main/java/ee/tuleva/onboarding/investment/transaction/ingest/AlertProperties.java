package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("transaction-registry.alerts")
public record AlertProperties(List<String> to, List<String> cc) {

  public AlertProperties {
    to = to == null ? List.of() : List.copyOf(to);
    cc = cc == null ? List.of() : List.copyOf(cc);
  }
}
