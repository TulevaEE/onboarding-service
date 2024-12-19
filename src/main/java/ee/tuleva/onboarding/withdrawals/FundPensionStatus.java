package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.pillar.Pillar.SECOND;
import static ee.tuleva.onboarding.pillar.Pillar.THIRD;

import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto.FundPensionDto;
import ee.tuleva.onboarding.pillar.Pillar;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

public record FundPensionStatus(List<FundPension> fundPensions) {
  public static FundPensionStatus from(FundPensionStatusDto statusDto) {
    var secondPillarFunds =
        statusDto.secondPillarFundPensions().stream().map(dto -> FundPension.from(dto, SECOND));
    var thirdPillarFunds =
        statusDto.thirdPillarFundPensions().stream().map(dto -> FundPension.from(dto, THIRD));

    return new FundPensionStatus(Stream.concat(secondPillarFunds, thirdPillarFunds).toList());
  }

  public record FundPension(
      Pillar pillar, Instant startDate, Instant endDate, int durationYears, boolean active) {

    public static FundPension from(FundPensionDto fundPensionDto, Pillar pillar) {
      return new FundPension(
          pillar,
          fundPensionDto.startDate(),
          fundPensionDto.endDate(),
          fundPensionDto.durationYears(),
          fundPensionDto.active());
    }
  }
}
