package ee.tuleva.onboarding.withdrawals;

import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionStatusDto.FundPension;

public record FundPensionStatus(boolean secondPillarActiveFundPension, boolean thirdPillarActiveFundPension) {
  public static FundPensionStatus from(FundPensionStatusDto statusDto) {
    boolean secondPillarActive = statusDto.secondPillarFundPensions().stream().anyMatch(FundPension::active);
    boolean thirdPillarActive = statusDto.thirdPillarFundPensions().stream().anyMatch(FundPension::active);

    return new FundPensionStatus(secondPillarActive, thirdPillarActive);
  }

  public record FundPension()
}
