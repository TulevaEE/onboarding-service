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
    @Nullable LocalDate latestNavDate,
    List<Deviation> outsideToleranceAtLatestNav,
    @Nullable LocalDate inLineSince,
    LocalDate deadline,
    LocalDate warningFrom) {

  public RebalanceState state() {
    if (inLine()) {
      return OK;
    }
    if (checkDate.isAfter(deadline)) {
      return FAIL;
    }
    if (!checkDate.isBefore(warningFrom)) {
      return WARNING;
    }
    return INFO;
  }

  public boolean inLine() {
    return inLineSince != null;
  }

  public Optional<LocalDate> closedOn() {
    return Optional.ofNullable(inLineSince);
  }

  public long daysSinceModelEffectiveDate() {
    return DAYS.between(modelEffectiveDate, checkDate);
  }

  public long daysUntilDeadline() {
    return DAYS.between(checkDate, deadline);
  }
}
