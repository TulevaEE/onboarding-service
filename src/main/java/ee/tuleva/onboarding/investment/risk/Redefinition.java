package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

sealed interface Redefinition {

  LocalDate date();

  record HoldingPeriod(LocalDate date, @Nullable String previousTradingDays, String tradingDays)
      implements Redefinition {}

  record PublicationRule(
      LocalDate date, @Nullable Integer previousRiskClass, @Nullable Integer riskClass)
      implements Redefinition {}
}
