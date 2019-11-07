package ee.tuleva.onboarding.account

import com.google.common.collect.Lists
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager

import static ee.tuleva.onboarding.conversion.UserConversionService.EXIT_RESTRICTED_FUND

class AccountStatementFixture {

    public static List<FundBalance> activeTuleva2ndPillarFundBalance = Lists.asList(
        FundBalance.builder()
            .value(100.0)
            .unavailableValue(0.0)
            .activeContributions(true)
            .pillar(2)
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("AE123232331")
                    .nameEstonian("Tuleva maailma aktsiate pensionifond")
                    .nameEnglish("Tuleva world stock pensionfund")
                    .id(123)
                    .pillar(2)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder().
                    isin("AE123232332")
                    .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .nameEnglish("Tuleva world bond pensionfund")
                    .id(124)
                    .pillar(2)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("AE123232337")
                    .nameEstonian("LHV XL")
                    .nameEnglish("LHV XL")
                    .id(123)
                    .pillar(2)
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
            .contributionSum(90.0)
            .pillar(2)
            .fund(
                Fund.builder()
                    .isin("AE123232332")
                    .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .nameEnglish("Tuleva world bond pensionfund")
                    .id(124)
                    .pillar(2)
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
            .contributionSum(0.0)
            .fund(
                Fund.builder()
                    .isin("AE1232322222")
                    .nameEstonian("LHV fund")
                    .nameEnglish("LHV fund")
                    .pillar(2)
                    .id(123)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("AE123232332")
                    .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .nameEnglish("Tuleva world bond pensionfund")
                    .pillar(2)
                    .id(124)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("EE645")
                    .nameEstonian("Tuleva III Samba Pensionifond")
                    .nameEnglish("Tuleva III Pillar Pension Fund")
                    .id(123)
                    .pillar(3)
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
            .contributionSum(190.0)
            .fund(
                Fund.builder()
                    .isin(EXIT_RESTRICTED_FUND)
                    .nameEstonian("Swedbank Pensionifond V100 indeks (väljumine piiratud)")
                    .nameEnglish("Swedbank V100 Index Pension Fund (exit restricted)")
                    .id(323)
                    .pillar(3)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("EE7654")
                    .nameEstonian("LHV Pensionifond Indeks Pluss")
                    .nameEnglish("LHV Pension Fund Index Plus")
                    .pillar(3)
                    .id(123)
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
            .contributionSum(90.0)
            .pillar(3)
            .fund(
                Fund.builder()
                    .isin("EE645")
                    .nameEstonian("Tuleva III Samba Pensionifond")
                    .nameEnglish("Tuleva III Pillar Pension Fund")
                    .id(124)
                    .pillar(3)
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
            .contributionSum(0.0)
            .fund(
                Fund.builder()
                    .isin("AE1232322222")
                    .nameEstonian("LHV fund")
                    .nameEnglish("LHV fund")
                    .id(123)
                    .pillar(3)
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
            .contributionSum(90.0)
            .fund(
                Fund.builder()
                    .isin("EE645")
                    .nameEstonian("Tuleva III Samba Pensionifond")
                    .nameEnglish("Tuleva III Pillar Pension Fund")
                    .id(124)
                    .pillar(3)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("Tuleva")
                            .build()
                    ).build()
            ).build()
    )

}
