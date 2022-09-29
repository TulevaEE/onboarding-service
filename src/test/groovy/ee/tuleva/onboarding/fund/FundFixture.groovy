package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.fund.manager.FundManager
import spock.lang.Specification

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE

class FundFixture extends Specification {

  public static tuleva3rdPillarFund =
      Fund.builder()
          .isin("EE645")
          .nameEstonian("Tuleva III Samba Pensionifond")
          .nameEnglish("Tuleva III Pillar Pension Fund")
          .shortName("TUV100")
          .id(123)
          .pillar(3)
          .status(ACTIVE)
          .ongoingChargesFigure(0.005)
          .managementFeeRate(0.0034)
          .fundManager(
              FundManager.builder()
                  .id(123)
                  .name("Tuleva")
                  .build()
          ).build()

}
