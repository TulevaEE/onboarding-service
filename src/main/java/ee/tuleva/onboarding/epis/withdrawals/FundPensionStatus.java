package ee.tuleva.onboarding.epis.withdrawals;

import java.time.Instant;
import java.util.List;

public record FundPensionStatus(
    List<FundPension> secondPillarFundPensions, List<FundPension> thirdPillarFundPensions) {
  public record FundPension(
      Instant startDate, Instant endDate, int durationYears, boolean active) {}
}
