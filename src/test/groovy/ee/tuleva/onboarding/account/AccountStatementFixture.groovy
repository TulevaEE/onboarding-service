package ee.tuleva.onboarding.account

import com.google.common.collect.Lists
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager

import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE

class AccountStatementFixture {

  public static List<FundBalance> activeTuleva2ndPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .pillar(2)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("AE123232331")
                  .nameEstonian("Tuleva maailma aktsiate pensionifond")
                  .nameEnglish("Tuleva world stock pensionfund")
                  .shortName("TUK75")
                  .id(123)
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
          ).build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .pillar(2)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
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
          ).build()
  )

  public static List<FundBalance> activeExternal2ndPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .pillar(2)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
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
          ).build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .contributions(90.0)
          .subtractions(0.0)
          .pillar(2)
          .currency("EUR")
          .fund(
              Fund.builder()
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
          ).build()
  )

  public static List<FundBalance> inactiveTuleva2ndPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .pillar(2)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("AE1232322222")
                  .nameEstonian("LHV fund")
                  .nameEnglish("LHV fund")
                  .shortName("LXK00")
                  .pillar(2)
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
          ).build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .pillar(2)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("AE123232332")
                  .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                  .nameEnglish("Tuleva world bond pensionfund")
                  .shortName("TUK00")
                  .pillar(2)
                  .id(124)
                  .status(ACTIVE)
                  .ongoingChargesFigure(0.005)
                  .managementFeeRate(0.0034)
                  .fundManager(
                      FundManager.builder()
                          .id(123)
                          .name("Tuleva")
                          .build()
                  ).build()
          ).build()
  )

  public static List<FundBalance> activeTuleva3rdPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .pillar(3)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
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
          ).build(),
      FundBalance.builder()
          .value(200.0)
          .unavailableValue(0.0)
          .activeContributions(false)
          .pillar(3)
          .contributions(190.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
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
          ).build()
  )

  public static List<FundBalance> activeExternal3rdPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .pillar(3)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
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
          ).build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .contributions(90.0)
          .subtractions(0.0)
          .pillar(3)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("EE645")
                  .nameEstonian("Tuleva III Samba Pensionifond")
                  .nameEnglish("Tuleva III Pillar Pension Fund")
                  .shortName("TUV100")
                  .id(124)
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
          ).build()
  )

  public static List<FundBalance> activeTuleva3rdPillarFund = Lists.asList(
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .activeContributions(true)
          .pillar(3)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
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
          ).build()
  )

  public static List<FundBalance> inactiveTuleva3rdPillarFundBalance = Lists.asList(
      FundBalance.builder()
          .value(0.0)
          .unavailableValue(0.0)
          .pillar(3)
          .activeContributions(true)
          .contributions(0.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("AE1232322222")
                  .nameEstonian("LHV fund")
                  .nameEnglish("LHV fund")
                  .shortName("LXK00")
                  .id(123)
                  .pillar(3)
                  .status(ACTIVE)
                  .ongoingChargesFigure(0.01)
                  .managementFeeRate(0.008)
                  .fundManager(
                      FundManager.builder()
                          .id(123)
                          .name("LHV")
                          .build()
                  ).build()
          ).build(),
      FundBalance.builder()
          .value(100.0)
          .unavailableValue(0.0)
          .pillar(3)
          .contributions(90.0)
          .subtractions(0.0)
          .currency("EUR")
          .fund(
              Fund.builder()
                  .isin("EE645")
                  .nameEstonian("Tuleva III Samba Pensionifond")
                  .nameEnglish("Tuleva III Pillar Pension Fund")
                  .shortName("TUV100")
                  .id(124)
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
          ).build()
  )

}
