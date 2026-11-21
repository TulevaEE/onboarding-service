package ee.tuleva.onboarding.investment.risk;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

sealed interface Redefinition {

  LocalDate date();

  record HoldingPeriod(LocalDate date, @Nullable String previousTradingDays, String tradingDays)
      implements Redefinition {}

  record Sources(
      LocalDate date,
      String previousSourceKeys,
      String sourceKeys,
      @Nullable Integer previousRiskClass,
      @Nullable Integer riskClass)
      implements Redefinition {

    boolean changedTheClass() {
      return !Objects.equals(previousRiskClass, riskClass);
    }
  }

  record PublicationRule(
      LocalDate date, @Nullable Integer previousRiskClass, @Nullable Integer riskClass)
      implements Redefinition {}
}
