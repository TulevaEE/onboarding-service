package ee.tuleva.onboarding.account

import com.google.common.collect.Lists
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager

class AccountStatementFixture {

    public static List<FundBalance> sampleConvertedFundBalanceWithActiveTulevaFund = Lists.asList(
        FundBalance.builder()
            .value(100)
            .activeContributions(true)
            .fund(
                Fund.builder().
                    isin("AE123232331").
                    nameEstonian("Tuleva maailma aktsiate pensionifond")
                    .id(123)
                    .fundManager(
                        FundManager.builder()
                            .id(123)
                            .name("Tuleva")
                            .build()
                    ).build()
            ).build(),
        FundBalance.builder()
            .value(100)
            .fund(
                Fund.builder().
                    isin("AE123232332").
                    nameEstonian("Tuleva maailma võlakirjade pensionifond")
                    .id(124)
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
                Fund.builder().
                    isin("AE123232337").
                    nameEstonian("LHV XL")
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
                Fund.builder().
                    isin("AE123232332").
                    nameEstonian("Tuleva maailma võlakirjade pensionifond")
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
                Fund.builder().
                    isin("AE1232322222").
                    nameEstonian("LHV fund")
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
