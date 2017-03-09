package ee.tuleva.onboarding.mandate

import java.time.Instant;

public class MandateFixture {

    public static CreateMandateCommand sampleCreateMandateCommand() {
        return [
                "fundTransferExchanges": [
                        new MandateFundTransferExchangeCommand(
                                "amount": 0.88,
                                "sourceFundIsin": "SOMEISIN",
                                "targetFundIsin": "AE123232334"
                        )
                ],
                "futureContributionFundIsin": "AE123232334"
        ]
    }

    public static Mandate sampleMandate() {
        Mandate mandate = Mandate.builder()
                .fundTransferExchanges([
                FundTransferExchange.builder()
                        .id(1234)
                        .sourceFundIsin("AE123232331")
                        .targetFundIsin("AE123232334")
                        .amount(new BigDecimal(0.2))
                        .build(),
                FundTransferExchange.builder()
                        .id(1235)
                        .sourceFundIsin("AE123232331")
                        .targetFundIsin("AE123232334")
                        .amount(new BigDecimal(0.8))
                        .build(),
                FundTransferExchange.builder()
                        .id(1236)
                        .sourceFundIsin("AE123232337")
                        .targetFundIsin("AE123232334")
                        .amount(new BigDecimal(1))
                        .build()
        ])
                .futureContributionFundIsin("AE123232334")
                .build()

        mandate.setId(123)
        mandate.setCreatedDate(Instant.now())
        mandate.setMandate("file".getBytes())
        return mandate
    }

}
