package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record R16Reserve(
    Basis basis,
    List<R16TypeForecast> types,
    @Nullable BigDecimal reserveSoft,
    @Nullable BigDecimal reserveHard,
    BigDecimal reserved) {

  enum Basis {
    NO_R16_THIS_MONTH,
    COVERED_BY_R45,
    FORECAST_NOT_YET_BOOKED
  }

  static final R16Reserve NONE =
      new R16Reserve(Basis.NO_R16_THIS_MONTH, List.of(), null, null, ZERO);
}
