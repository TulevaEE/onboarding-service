package ee.tuleva.onboarding.epis.withdrawals;

import java.time.Instant;
import java.util.List;

public record FundPensionStatusDto(
    List<FundPensionDto> secondPillarFundPensions, List<FundPensionDto> thirdPillarFundPensions) {
  public record FundPensionDto(
      Instant startDate, Instant endDate, int durationYears, boolean active) {}
}
