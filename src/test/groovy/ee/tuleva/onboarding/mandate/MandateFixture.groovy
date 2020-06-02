package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand

import java.time.Instant

import static ee.tuleva.onboarding.mandate.Mandate.*

class MandateFixture {

    public static futureContibutionFundIsin = "AE123232334"

    static CreateMandateCommand sampleCreateMandateCommand() {
        return [
            "fundTransferExchanges"     : [
                new MandateFundTransferExchangeCommand(
                    "amount": 0.88,
                    "sourceFundIsin": "SOMEISIN",
                    "targetFundIsin": futureContibutionFundIsin
                )
            ],
            "futureContributionFundIsin": futureContibutionFundIsin
        ]
    }

    static sampleStartIdCardSignCommand(String clientCertificate) {
        return new StartIdCardSignCommand(clientCertificate: clientCertificate)
    }

    static sampleFinishIdCardSignCommand(String signedHash) {
        return new FinishIdCardSignCommand(signedHash: signedHash)
    }

    static CreateMandateCommand invalidCreateMandateCommand() {
        return [
            "fundTransferExchanges"     : [
                new MandateFundTransferExchangeCommand(
                    "amount": 0.88,
                    "sourceFundIsin": "SOMEISIN",
                    "targetFundIsin": futureContibutionFundIsin
                ),
                new MandateFundTransferExchangeCommand(
                    "amount": 0.90,
                    "sourceFundIsin": "SOMEISIN",
                    "targetFundIsin": futureContibutionFundIsin
                )
            ],
            "futureContributionFundIsin": futureContibutionFundIsin
        ]
    }

    static CreateMandateCommand invalidCreateMandateCommandWithSameSourceAndTargetFund =
        [
            "fundTransferExchanges"     : [
                new MandateFundTransferExchangeCommand(
                    "amount": 0.88,
                    "sourceFundIsin": "SOMEOTHER",
                    "targetFundIsin": futureContibutionFundIsin
                ),
                new MandateFundTransferExchangeCommand(
                    "amount": 0.90,
                    "sourceFundIsin": "SOMEISIN",
                    "targetFundIsin": "SOMEISIN"
                )
            ],
            "futureContributionFundIsin": futureContibutionFundIsin
        ];

    static MandateBuilder emptyMandate() {
        builder()
            .futureContributionFundIsin("isin")
            .fundTransferExchanges([])
            .pillar(2)
    }

    static Mandate sampleMandate() {
        Mandate mandate = builder()
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
        mandate.setPillar(2)
        return mandate
    }

    static Mandate sampleMandateWithEmptyTransfer() {
        Mandate mandate = builder()
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
                    .build(),
                FundTransferExchange.builder()
                    .id(1236)
                    .sourceFundIsin("AE123232337")
                    .targetFundIsin(futureContibutionFundIsin)
                    .amount(BigDecimal.ZERO)
                    .build()

            ])
            .futureContributionFundIsin(futureContibutionFundIsin)
            .build()

        mandate.setId(123)
        mandate.setCreatedDate(Instant.now())
        mandate.setMandate("file".getBytes())
        return mandate
    }

    static Mandate sampleUnsignedMandate() {
        Mandate mandate = builder()
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
        return mandate
    }

    static List<Fund> sampleFunds() {
        return Arrays.asList(
            Fund.builder().
                isin(futureContibutionFundIsin)
                .nameEstonian("Tuleva maailma aktsiate pensionifond")
                .nameEnglish("Tuleva World Stock Fund")
                .shortName("TUK75")
                .id(123)
                .fundManager(
                    FundManager.builder()
                        .id(123)
                        .name("Tuleva")
                        .build()
                )
                .build(),
            Fund.builder().isin("EE3600019775")
                .nameEstonian("SEB fond")
                .nameEnglish("SEB fund")
                .shortName("SEB123")
                .fundManager(
                    FundManager.builder()
                        .id(124)
                        .name("SEB")
                        .build()
                )
                .build(),
            Fund.builder().isin("EE3600019776")
                .nameEstonian("LHV XL")
                .nameEnglish("LHV XL eng")
                .shortName("LXK75")
                .fundManager(
                    FundManager.builder()
                        .id(125)
                        .name("LHV")
                        .build()
                )
                .build(),
            Fund.builder().isin("EE3600019777")
                .nameEstonian("Swedbänk fond")
                .nameEnglish("Swedbank fund")
                .shortName("SWE123")
                .fundManager(
                    FundManager.builder()
                        .id(126)
                        .name("Swedbank")
                        .build()
                )
                .build(),
            Fund.builder().isin("AE123232331")
                .nameEstonian("Nordea fond")
                .nameEnglish("Nordea fund")
                .shortName("ND123")
                .fundManager(
                    FundManager.builder()
                        .id(127)
                        .name("Nordea")
                        .build()
                )
                .build(),
            Fund.builder().isin("AE123232337")
                .nameEstonian("LHV S")
                .nameEnglish("LHV S eng")
                .shortName("LXK00")
                .fundManager(
                    FundManager.builder()
                        .id(125)
                        .name("LHV")
                        .build()
                )
                .build()
        )
    }

}
