package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.FAIL;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.INFO;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.WARNING;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record RebalanceWindow(
    TulevaFund fund,
    LocalDate modelEffectiveDate,
    LocalDate checkDate,
    BigDecimal tolerance,
    Set<String> changedIsins,
    Set<String> tradedIsins,
    @Nullable LocalDate latestNavDate,
    List<Deviation> outsideToleranceAtLatestNav,
    @Nullable LocalDate inLineSince,
    RebalanceDeadlines deadlines) {

  public RebalanceState state() {
    if (inLine()) {
      return OK;
    }
    if (!checkDate.isBefore(deadlines.inLineDue())) {
      return FAIL;
    }
    if (!checkDate.isBefore(deadlines.tradesDue()) && !tradesDone()) {
      return WARNING;
    }
    return INFO;
  }

  public boolean inLine() {
    return inLineSince != null;
  }

  public boolean tradesDone() {
    return !tradedIsins.isEmpty();
  }

  public long daysSinceModelEffectiveDate() {
    return DAYS.between(modelEffectiveDate, checkDate);
  }

  long daysUntilInLineDue() {
    return DAYS.between(checkDate, deadlines.inLineDue());
  }

  public Optional<LocalDate> closedOn() {
    return Optional.ofNullable(inLineSince);
  }
}
