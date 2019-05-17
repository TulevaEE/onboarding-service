package ee.tuleva.onboarding.account

import com.google.common.collect.Lists
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager

class AccountStatementFixture {

    public static List<FundBalance> sampleConvertedFundBalanceWithActiveTulevaFund = Lists.asList(
        FundBalance.builder()
            .value(100)
            .activeContributions(true)
            .pillar(2)
            .fund(
                Fund.builder().
                    isin("AE123232331").
                    nameEstonian("Tuleva maailma aktsiate pensionifond")
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
            .value(100)
            .pillar(2)
            .fund(
                Fund.builder().
                    isin("AE123232332").
                    nameEstonian("Tuleva maailma võlakirjade pensionifond")
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

    public static List<FundBalance> sampleNonConvertedFundBalanceWithActiveNonTulevaFund = Lists.asList(
        FundBalance.builder()
            .value(100)
            .activeContributions(true)
            .fund(
                Fund.builder()
                    .isin("AE123232337")
                    .nameEstonian("LHV XL")
                    .nameEnglish("LHV XL")
                    .id(123)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("LHV")
                            .build()
                    ).build()
            ).build(),
        FundBalance.builder()
            .value(100)
            .fund(
                Fund.builder()
                    .isin("AE123232332")
                    .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .nameEnglish("Tuleva world bond pensionfund")
                    .id(124)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("Tuleva")
                            .build()
                    ).build()
            ).build()
    )

    public static List<FundBalance> sampleConvertedFundBalanceWithNonActiveTulevaFund = Lists.asList(
        FundBalance.builder()
            .value(0)
            .activeContributions(true)
            .fund(
                Fund.builder()
                    .isin("AE1232322222")
                    .nameEstonian("LHV fund")
                    .nameEnglish("LHV fund")
                    .id(123)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("LHV")
                            .build()
                    ).build()
            ).build(),
        FundBalance.builder()
            .value(100)
            .fund(
                Fund.builder()
                    .isin("AE123232332")
                    .nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .nameEnglish("Tuleva world bond pensionfund")
                    .id(124)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("Tuleva")
                            .build()
                    ).build()
            ).build()
    )

}
