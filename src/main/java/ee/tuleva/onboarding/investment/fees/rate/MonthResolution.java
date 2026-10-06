package ee.tuleva.onboarding.investment.fees.rate;

import java.time.YearMonth;
import java.util.List;

sealed interface MonthResolution {

  YearMonth month();

  record Resolved(YearMonth month, List<InstrumentRate> rates) implements MonthResolution {}

  record Failed(YearMonth month, String reason) implements MonthResolution {}
}
