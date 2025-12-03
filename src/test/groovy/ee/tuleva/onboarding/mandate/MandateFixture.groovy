package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.epis.mandate.details.*
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.manager.FundManager
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand
import ee.tuleva.onboarding.mandate.command.MandateFundTransferExchangeCommand
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand
import ee.tuleva.onboarding.mandate.generic.MandateDto

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.mandate.details.BankAccountDetails.BankAccountType.ESTONIAN
import static ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.SIX
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.pillar.Pillar.THIRD
import static ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails.fromFundTransferExchanges
import static ee.tuleva.onboarding.mandate.Mandate.MandateBuilder
import static ee.tuleva.onboarding.mandate.Mandate.builder
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class MandateFixture {

  public static PartialWithdrawalMandateDetails aPartialWithdrawalMandateDetails = new PartialWithdrawalMandateDetails(SECOND,
      new BankAccountDetails(ESTONIAN, "EE591254471322749514"),
      List.of(new PartialWithdrawalMandateDetails.FundWithdrawalAmount("EE3600109435", 10, BigDecimal.valueOf(20)),
          new PartialWithdrawalMandateDetails.FundWithdrawalAmount("EE3600019766", 5, BigDecimal.valueOf(30))),
      "EST"
  )

  public static PartialWithdrawalMandateDetails aThirdPillarPartialWithdrawalMandateDetails = new PartialWithdrawalMandateDetails(THIRD,
      new BankAccountDetails(ESTONIAN, "EE591254471322749514"),
      List.of(new PartialWithdrawalMandateDetails.FundWithdrawalAmount("EE3600109435", 10, BigDecimal.valueOf(20)),
          new PartialWithdrawalMandateDetails.FundWithdrawalAmount("EE3600109435", 5, BigDecimal.valueOf(30))),
      "EST"
  )

  public static FundPensionOpeningMandateDetails aFundPensionOpeningMandateDetails = new FundPensionOpeningMandateDetails(SECOND,
      new FundPensionOpeningMandateDetails.FundPensionDuration(20, false),
      new BankAccountDetails(ESTONIAN, "EE591254471322749514")
  )

  public static FundPensionOpeningMandateDetails aThirdPillarFundPensionOpeningMandateDetails = new FundPensionOpeningMandateDetails(THIRD,
      new FundPensionOpeningMandateDetails.FundPensionDuration(20, true),
      new BankAccountDetails(ESTONIAN, "EE591254471322749514")
  )

  public static PaymentRateChangeMandateDetails aPaymentRateChangeMandateDetails = new PaymentRateChangeMandateDetails(SIX)

  public static futureContibutionFundIsin = "AE123232334"

  static CreateMandateCommand sampleCreateMandateCommand() {
    return new CreateMandateCommand(
        "fundTransferExchanges": [
            new MandateFundTransferExchangeCommand(
                "amount": 0.88,
                "sourceFundIsin": "SOMEISIN",
                "targetFundIsin": futureContibutionFundIsin
            )
        ],
        "futureContributionFundIsin": futureContibutionFundIsin,
        "address": countryFixture().build()
    )
  }

  static <TDetails extends MandateDetails> MandateDto<TDetails> sampleMandateCreationDto(TDetails details) {
    return MandateDto.builder().details(details).build()
  }

  static StartIdCardSignCommand sampleStartIdCardSignCommand(String clientCertificate) {
    return new StartIdCardSignCommand(clientCertificate: clientCertificate)
  }

  static FinishIdCardSignCommand sampleFinishIdCardSignCommand(String signedHash) {
    return new FinishIdCardSignCommand(signedHash: signedHash)
  }

  static CreateMandateCommand invalidCreateMandateCommand() {
    return new CreateMandateCommand(
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
    )
  }

  static CreateMandateCommand invalidCreateMandateCommandWithSameSourceAndTargetFund =
      new CreateMandateCommand(
          "fundTransferExchanges": [
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
      )

  static MandateBuilder emptyMandate() {
    builder()
        .futureContributionFundIsin("isin")
        .fundTransferExchanges([])
        .address(countryFixture().build())
        .metadata([:])
        .pillar(SECOND.toInt())
  }

  static Mandate sampleMandate() {
    Mandate mandate = builder()
        .fundTransferExchanges([
            FundTransferExchange.builder()
                .id(1234)
                .sourceFundIsin("EE3600019790")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.2"))
                .build(),
            FundTransferExchange.builder()
                .id(1235)
                .sourceFundIsin("EE3600019790")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.8"))
                .build(),
            FundTransferExchange.builder()
                .id(1236)
                .sourceFundIsin("AE123232337")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("1"))
                .build()
        ])
        .futureContributionFundIsin(futureContibutionFundIsin)
        .address(countryFixture().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setPillar(SECOND.toInt())
    mandate.setMetadata([:])
    return mandate
  }

  static Mandate thirdPillarMandate() {
    Mandate mandate = builder()
        .fundTransferExchanges([
            FundTransferExchange.builder()
                .id(1234)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.2"))
                .build(),
            FundTransferExchange.builder()
                .id(1235)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.8"))
                .build(),
            FundTransferExchange.builder()
                .id(1236)
                .sourceFundIsin("AE123232337")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("1"))
                .build()
        ])
        .futureContributionFundIsin(futureContibutionFundIsin)
        .address(countryFixture().build())
        .build()

    mandate.setId(124)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setPillar(THIRD.toInt())
    return mandate
  }

  static Mandate sampleWithdrawalCancellationMandate() {
    Mandate mandate = builder()
        .address(countryFixture().build())
        .user(sampleUser().build())
        .details(new WithdrawalCancellationMandateDetails())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.pillar = SECOND.toInt()
    return mandate
  }

  static Mandate sampleEarlyWithdrawalCancellationMandate() {
    Mandate mandate = builder()
        .address(countryFixture().build())
        .fundTransferExchanges([])
        .user(sampleUser().build())
        .details(new EarlyWithdrawalCancellationMandateDetails())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.pillar = SECOND.toInt()
    return mandate
  }

  static Mandate samplePartialWithdrawalMandate(PartialWithdrawalMandateDetails details = aPartialWithdrawalMandateDetails) {
    Mandate mandate = builder()
        .address(countryFixture().build())
        .pillar(details.pillar.toInt())
        .details(details)
        .fundTransferExchanges([])
        .user(sampleUser().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setMetadata(Map.of())
    mandate.pillar = details.getPillar().toInt()
    return mandate
  }

  static Mandate sampleFundPensionOpeningMandate(FundPensionOpeningMandateDetails details = aFundPensionOpeningMandateDetails) {
    Mandate mandate = builder()
        .address(countryFixture().build())
        .pillar(details.pillar.toInt())
        .details(details)
        .fundTransferExchanges([])
        .user(sampleUser().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setMetadata(Map.of())
    mandate.pillar = details.getPillar().toInt()
    return mandate
  }

  static Mandate sampleMandateWithPaymentRate(PaymentRateChangeMandateDetails details = aPaymentRateChangeMandateDetails) {
    Mandate mandate = builder()
        .address(countryFixture().build())
        .pillar(SECOND.toInt())
        .details(details)
        .fundTransferExchanges([])
        .user(sampleUser().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setMetadata(Map.of())
    mandate.paymentRate = details.paymentRate.numericValue
    mandate.pillar = SECOND.toInt()
    return mandate
  }

  static Mandate sampleTransferCancellationMandate() {
    var fundTransferExchanges = [FundTransferExchange.builder()
                                     .id(1234)
                                     .sourceFundIsin("EE3600109435")
                                     .targetFundIsin(null)
                                     .amount(null)
                                     .build()]

    Mandate mandate = builder()
        .fundTransferExchanges(
            fundTransferExchanges
        )
        .futureContributionFundIsin(null)
        .user(sampleUser().build())
        .address(countryFixture().build())
        .details(fromFundTransferExchanges(fundTransferExchanges, SECOND.toInt()))
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setPillar(SECOND.toInt())
    return mandate
  }

  static Mandate sampleSelectionMandate() {
    Mandate mandate = builder()
        .fundTransferExchanges([])
        .futureContributionFundIsin(futureContibutionFundIsin)
        .address(countryFixture().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setPillar(SECOND.toInt())
    mandate.setDetails(new SelectionMandateDetails(futureContibutionFundIsin))
    return mandate
  }

  static Mandate sampleMandateWithEmptyTransfer() {
    Mandate mandate = builder()
        .fundTransferExchanges([
            FundTransferExchange.builder()
                .id(1234)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.2"))
                .build(),
            FundTransferExchange.builder()
                .id(1235)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.8"))
                .build(),
            FundTransferExchange.builder()
                .id(1236)
                .sourceFundIsin("AE123232337")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("1"))
                .build(),
            FundTransferExchange.builder()
                .id(1236)
                .sourceFundIsin("AE123232337")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(BigDecimal.ZERO)
                .build()

        ])
        .futureContributionFundIsin(futureContibutionFundIsin)
        .address(countryFixture().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    mandate.setMandate("file".getBytes())
    mandate.setPillar(SECOND.toInt())
    return mandate
  }

  static Mandate sampleUnsignedMandate() {
    Mandate mandate = builder()
        .fundTransferExchanges([
            FundTransferExchange.builder()
                .id(1234)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.2"))
                .build(),
            FundTransferExchange.builder()
                .id(1235)
                .sourceFundIsin("AE123232331")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("0.8"))
                .build(),
            FundTransferExchange.builder()
                .id(1236)
                .sourceFundIsin("AE123232337")
                .targetFundIsin(futureContibutionFundIsin)
                .amount(new BigDecimal("1"))
                .build()
        ])
        .futureContributionFundIsin(futureContibutionFundIsin)
        .address(countryFixture().build())
        .build()

    mandate.setId(123)
    mandate.setCreatedDate(Instant.parse("2021-03-10T12:00:00Z"))
    return mandate
  }

  static List<Fund> sampleFunds() {
    return Arrays.asList(
        Fund.builder()
            .isin(futureContibutionFundIsin)
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
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600109443")
            .nameEstonian("Tuleva maailma võlakirjade pensionifond")
            .nameEnglish("Tuleva World Bonds Pension Fund")
            .shortName("TUK00")
            .id(234)
            .fundManager(
                FundManager.builder()
                    .id(123)
                    .name("Tuleva")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019775")
            .nameEstonian("SEB fond")
            .nameEnglish("SEB fund")
            .shortName("SEB123")
            .fundManager(
                FundManager.builder()
                    .id(124)
                    .name("SEB")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019776")
            .nameEstonian("LHV XL")
            .nameEnglish("LHV XL eng")
            .shortName("LXK75")
            .fundManager(
                FundManager.builder()
                    .id(125)
                    .name("LHV")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("EE3600019777")
            .nameEstonian("Swedbänk fond")
            .nameEnglish("Swedbank fund")
            .shortName("SWE123")
            .fundManager(
                FundManager.builder()
                    .id(126)
                    .name("Swedbank")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("AE123232331")
            .nameEstonian("Nordea fond")
            .nameEnglish("Nordea fund")
            .shortName("ND123")
            .fundManager(
                FundManager.builder()
                    .id(127)
                    .name("Nordea")
                    .build()
            )
            .pillar(2)
            .build(),
        Fund.builder()
            .isin("AE123232337")
            .nameEstonian("LHV S")
            .nameEnglish("LHV S eng")
            .shortName("LXK00")
            .fundManager(
                FundManager.builder()
                    .id(125)
                    .name("LHV")
                    .build()
            )
            .pillar(2)
            .build()
    )
  }

}
