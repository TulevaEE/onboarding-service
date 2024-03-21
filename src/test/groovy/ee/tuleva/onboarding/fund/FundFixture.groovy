package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.fund.manager.FundManager

import java.time.LocalDate

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE

class FundFixture {

  static Fund tuleva2ndPillarStockFund() {
    return Fund.builder()
        .isin("AE123232331")
        .nameEstonian("Tuleva maailma aktsiate pensionifond")
        .nameEnglish("Tuleva world stock pensionfund")
        .shortName("TUK75")
        .id(123)
        .pillar(2)
        .status(ACTIVE)
        .ongoingChargesFigure(0.005)
        .managementFeeRate(0.0034)
        .inceptionDate(LocalDate.parse("2019-01-01"))
        .fundManager(
            FundManager.builder()
                .id(123)
                .name("Tuleva")
                .build()
        ).build()
  }

  static Fund tuleva2ndPillarBondFund() {

    return Fund.builder()
        .isin("AE123232332")
        .nameEstonian("Tuleva maailma võlakirjade pensionifond")
        .nameEnglish("Tuleva world bond pensionfund")
        .shortName("TUK00")
        .id(124)
        .pillar(2)
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

  static tuleva3rdPillarFund() {
    return Fund.builder()
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

  public static lhv2ndPillarFund() {
    return Fund.builder()
        .isin("AE123232337")
        .nameEstonian("LHV XL")
        .nameEnglish("LHV XL")
        .shortName("LXK75")
        .id(123)
        .pillar(2)
        .status(ACTIVE)
        .ongoingChargesFigure(0.01)
        .managementFeeRate(0.008)
        .fundManager(
            FundManager.builder()
                .id(123)
                .name("LHV")
                .build()
        ).build()
  }

  static exitRestricted3rdPillarFund() {
    return Fund.builder()
        .isin(Fund.EXIT_RESTRICTED_FUND_ISIN)
        .nameEstonian("Swedbank Pensionifond V100 indeks (väljumine piiratud)")
        .nameEnglish("Swedbank V100 Index Pension Fund (exit restricted)")
        .shortName("SWV100")
        .id(323)
        .pillar(3)
        .status(ACTIVE)
        .ongoingChargesFigure(0.006)
        .managementFeeRate(0.004)
        .fundManager(
            FundManager.builder()
                .id(345)
                .name("Swedbank")
                .build()
        ).build()
  }

  static lhv3rdPillarFund() {
    return Fund.builder()
        .isin("EE7654")
        .nameEstonian("LHV Pensionifond Indeks Pluss")
        .nameEnglish("LHV Pension Fund Index Plus")
        .shortName("LIT100")
        .pillar(3)
        .id(123)
        .status(ACTIVE)
        .ongoingChargesFigure(0.01)
        .managementFeeRate(0.008)
        .fundManager(
            FundManager.builder()
                .id(123)
                .name("LHV")
                .build()
        ).build()
  }

}
