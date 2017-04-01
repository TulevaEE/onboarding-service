package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundManager
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand

import java.time.Instant;

class MandateFixture {

    public static futureContibutionFundIsin = "AE123232334"

    static CreateMandateCommand sampleCreateMandateCommand() {
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

    static sampleStartIdCardSignCommand(String clientCertificate) {
        return new StartIdCardSignCommand(clientCertificate: clientCertificate)
    }

    static sampleFinishIdCardSignCommand(String signedHash) {
        return new FinishIdCardSignCommand(signedHash: signedHash)
    }

    static CreateMandateCommand invalidCreateMandateCommand() {
        return [
                "fundTransferExchanges": [
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

    static Mandate sampleMandate() {
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

    static Mandate sampleUnsignedMandate() {
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
        return mandate
    }

    static List<Fund> sampleFunds() {
        return Arrays.asList(
                Fund.builder().
                        isin(futureContibutionFundIsin).
                        name("Tuleva maailma aktsiate pensionifond")
                        .id(123)
                        .fundManager(
                            FundManager.builder()
                            .id(123)
                            .name("Tuleva")
                            .build()
                        )
                        .build(),
                Fund.builder().isin("EE3600019775").name("SEB fond")
                        .fundManager(
                        FundManager.builder()
                                .id(124)
                                .name("SEB")
                                .build()
                )
                        .build(),
                Fund.builder().isin("EE3600019776").name("LHV XL")
                        .fundManager(
                        FundManager.builder()
                                .id(125)
                                .name("LHV")
                                .build()
                )
                        .build(),
                Fund.builder().isin("EE3600019777").name("Swedbänk fond")
                        .fundManager(
                        FundManager.builder()
                                .id(126)
                                .name("Swedbank")
                                .build()
                )
                        .build(),
                Fund.builder().isin("AE123232331").name("Nordea fond")
                        .fundManager(
                        FundManager.builder()
                                .id(127)
                                .name("Nordea")
                                .build()
                )
                        .build(),
                Fund.builder().isin("AE123232337").name("LHV S")
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
