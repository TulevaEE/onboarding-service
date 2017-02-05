package ee.tuleva.onboarding.mandate;

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
        return Mandate.builder()
                .fundTransferExchanges(null)
                .futureContributionFundIsin("sample isin")
                .build()
    }
}
