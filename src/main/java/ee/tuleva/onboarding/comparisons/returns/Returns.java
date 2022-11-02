package ee.tuleva.onboarding.comparisons.returns;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Returns {

  LocalDate from;
  List<Return> returns;

  Boolean notEnoughHistory;

  @Value
  @Builder
  public static class Return {

    String key;
    double value; // TODO: migrate to BigDecimal
    Type type;

    public enum Type {
      PERSONAL,
      FUND,
      INDEX
    }
  }
}
