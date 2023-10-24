package ee.tuleva.onboarding.comparisons.returns;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Returns {

  List<Return> returns;

  @Value
  @Builder
  public static class Return {

    String key;
    BigDecimal rate;
    BigDecimal amount;
    BigDecimal paymentsSum;
    Currency currency;
    Type type;
    @JsonIgnore LocalDate from;

    public enum Type {
      PERSONAL,
      FUND,
      INDEX
    }
  }

  public LocalDate getFrom() {
    return returns.stream()
        .reduce(
            (aReturn1, aReturn2) -> {
              if (!aReturn1.getFrom().equals(aReturn2.getFrom())) {
                throw new IllegalStateException(
                    "Returns have different fromDates: "
                        + aReturn1.getFrom()
                        + ", "
                        + aReturn2.getFrom());
              }
              return aReturn1;
            })
        .get()
        .getFrom();
  }
}
