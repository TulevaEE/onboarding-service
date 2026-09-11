package ee.tuleva.onboarding.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundNavValues {

  Optional<NavPoint> latestValueOnOrBefore(String isin, LocalDate date);

  Optional<NavPoint> lastValue(String isin);

  List<NavPoint> valuesBetween(String isin, LocalDate start, LocalDate end);

  record NavPoint(LocalDate date, BigDecimal value) {}
}
