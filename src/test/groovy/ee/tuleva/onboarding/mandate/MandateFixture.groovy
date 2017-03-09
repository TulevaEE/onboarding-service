package ee.tuleva.onboarding.mandate

import ee.tuleva.domain.fund.Fund

import java.time.Instant;

public class MandateFixture {

    public static futureContibutionFundIsin = "AE123232334"

    public static CreateMandateCommand sampleCreateMandateCommand() {
        return [
                "fundTransferExchanges": [
                        new MandateFundTransferExchangeCommand(
                                "amount": 0.88,
                                "sourceFundIsin": "SOMEISIN",
                                "targetFundIsin": futureContibutionFundIsin
                        )
                ],
                "futureContributionFundIsin": futureContibutionFundIsin
        ]
    }

    public static Mandate sampleMandate() {
        Mandate mandate = Mandate.builder()
                .fundTransferExchanges([
                FundTransferExchange.builder()
                        .id(1234)
                        .sourceFundIsin("AE123232331")
                        .targetFundIsin(futureContibutionFundIsin)
                        .amount(new BigDecimal(0.2))
                        .build(),
                FundTransferExchange.builder()
                        .id(1235)
                        .sourceFundIsin("AE123232331")
                        .targetFundIsin(futureContibutionFundIsin)
                        .amount(new BigDecimal(0.8))
                        .build(),
                FundTransferExchange.builder()
                        .id(1236)
                        .sourceFundIsin("AE123232337")
                        .targetFundIsin(futureContibutionFundIsin)
                        .amount(new BigDecimal(1))
                        .build()
        ])
                .futureContributionFundIsin(futureContibutionFundIsin)
                .build()

        mandate.setId(123)
        mandate.setCreatedDate(Instant.now())
        mandate.setMandate("file".getBytes())
        return mandate
    }

    public static List<Fund> sampleFunds() {
        return Arrays.asList(
                Fund.builder().isin(futureContibutionFundIsin).name("Tuleva fond").build(),
                Fund.builder().isin("EE3600019775").name("SEB fond").build(),
                Fund.builder().isin("EE3600019776").name("LHV XL").build(),
                Fund.builder().isin("EE3600019777").name("Swedbänk fond").build(),
                Fund.builder().isin("AE123232331").name("Nordea fond").build(),
                Fund.builder().isin("AE123232337").name("Hanzafond fond").build()
        );
    }

}
