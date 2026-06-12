package ee.tuleva.onboarding.investment.epis;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;

public record SettlementTimingWarning(
    Type type,
    TulevaFund fund,
    LocalDate sellSettlementDate,
    LocalDate deadlineDate,
    String message) {

  public enum Type {
    PEVA_DEADLINE_MISS,
    REBALANCE_GAP
  }
}
