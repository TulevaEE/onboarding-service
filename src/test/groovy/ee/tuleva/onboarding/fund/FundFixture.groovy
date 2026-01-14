package ee.tuleva.onboarding.fund

import ee.tuleva.onboarding.fund.manager.FundManager

import java.time.LocalDate

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE

class FundFixture {

  static Fund tuleva2ndPillarStockFund() {
    return Fund.builder()
        //TODO: use data that is actually initialized in V1_85.1__add_funds.sql
        .isin("EE3600109435")
//        .isin("AE123232331")
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
        .isin("EE3600109443")
//        .isin("AE123232332")
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

  static Fund tuleva3rdPillarFund() {
    return Fund.builder()
        .isin("EE3600001707")
//        .isin("EE645")
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

  static Fund lhv2ndPillarFund() {
    return Fund.builder()
        .isin("EE3600019766")
//        .isin("AE123232337")
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

  static Fund exitRestricted3rdPillarFund() {
    return Fund.builder()
        .isin(Fund.EXIT_RESTRICTED_FUND_ISINS.get(0))
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

  static Fund lhv3rdPillarFund() {
    return Fund.builder()
//        .isin("EE7654")
        .isin("EE3600109419")
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

  static Fund additionalSavingsFund() {
    return Fund.builder()
        .fundManager(new FundManager("Tuleva", 1L))
        .inceptionDate(LocalDate.of(2025, 10, 1))
        .isin("EE0000000000")
        .pillar(null)
        .nameEnglish("Tuleva Additional Savings Fund")
        .nameEstonian("Tuleva Täiendav Kogumisfond")
        .managementFeeRate(0.002)
        .ongoingChargesFigure(0.0045)
        .status(ACTIVE)
        .build()
  }
}
