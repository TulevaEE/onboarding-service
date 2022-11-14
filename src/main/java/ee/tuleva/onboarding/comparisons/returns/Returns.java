package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
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
    BigDecimal rate;

    BigDecimal amount;
    Currency currency;
    Type type;

    public enum Type {
      PERSONAL,
      FUND,
      INDEX
    }
  }
}
