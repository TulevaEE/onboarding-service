package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.application.PaymentApplicationDetails
import ee.tuleva.onboarding.payment.application.PaymentLinkingService
import ee.tuleva.onboarding.savings.fund.SavingFundPayment
import ee.tuleva.onboarding.savings.fund.SavingFundDeadlinesService
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService
import ee.tuleva.onboarding.savings.fund.application.SavingFundPaymentApplicationDetails
import ee.tuleva.onboarding.savings.fund.application.SavingFundWithdrawalApplicationDetails
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.ApplicationDtoFixture.*
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.paymentApplication
import static ee.tuleva.onboarding.mandate.application.ApplicationType.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.pillar.Pillar.THIRD
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.RESERVED
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED
import static java.math.BigDecimal.TEN
import static java.math.BigDecimal.valueOf

class ApplicationServiceSpec extends Specification {

  EpisService episService = Mock()
  LocaleService localeService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()
  PaymentLinkingService paymentApplicationService = Mock()
  SavingFundDeadlinesService savingFundPaymentDeadlinesService = Mock()
  SavingFundPaymentUpsertionService savingFundPaymentService = Mock()
  RedemptionService savingFundRedemptionService = Mock()

  ApplicationService applicationService =
      new ApplicationService(episService, localeService, fundRepository, mandateDeadlinesService, paymentApplicationService, savingFundPaymentDeadlinesService, savingFundPaymentService, savingFundRedemptionService)

  def "gets applications"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def transferApplication1 = sampleTransferApplicationDto()
    def completedTransferApplication = sampleTransferApplicationDto()
    completedTransferApplication.status = COMPLETE
    completedTransferApplication.id = 456L
    def transferApplication2 = sampleTransferApplicationDto()
    def withdrawalApplication = sampleWithdrawalApplicationDto()
    def earlyWithdrawalApplication = sampleEarlyWithdrawalApplicationDto()
    def pikTransferApplication = samplePikTransferApplicationDto()
    def paymentRateApplication = samplePendingPaymentRateApplicationDto()

    episService.getApplications(person) >> [
        transferApplication1, transferApplication2, completedTransferApplication,
        pikTransferApplication, withdrawalApplication, earlyWithdrawalApplication,
        paymentRateApplication
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> [paymentApplication().build()]
    savingFundPaymentService.getPendingPaymentsForUser(_) >> []
    savingFundRedemptionService.getPendingRedemptionsForUser(_) >> []

    when:
    def applications = applicationService.getAllApplications(person)

    then:
    with(applications[0] as Application<TransferApplicationDetails>) {
      id == 456L
      type == TRANSFER
      status == COMPLETE
      creationTime == TestClockHolder.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[1] as Application<TransferApplicationDetails>) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[2] as Application<TransferApplicationDetails>) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund.isin == "EE3600109443"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[3] as Application<TransferApplicationDetails>) {
      id == 123L
      type == TRANSFER
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        sourceFund.isin == "AE123232334"
        exchanges.size() == 1
        with(exchanges[0]) {
          sourceFund.isin == "AE123232334"
          targetFund == null
          targetPik == "EE801281685311741971"
          amount == 1.0
        }
        fulfillmentDate == LocalDate.parse("2021-05-03")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[4] as Application<WithdrawalApplicationDetails>) {
      id == 123L
      type == EARLY_WITHDRAWAL
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        depositAccountIBAN == "IBAN"
        fulfillmentDate == LocalDate.parse("2021-09-01")
        cancellationDeadline == Instant.parse("2021-07-31T20:59:59.999999999Z")
      }
    }
    with(applications[5] as Application<WithdrawalApplicationDetails>) {
      id == 123L
      type == WITHDRAWAL
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        depositAccountIBAN == "IBAN"
        fulfillmentDate == LocalDate.parse("2021-04-20")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
      }
    }
    with(applications[6] as Application<PaymentApplicationDetails>) {
      id == 123L
      type == PAYMENT
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        amount == 10.0
        currency == EUR
        targetFund == tuleva3rdPillarApiFundResponse()
      }
    }
    with(applications[7] as Application<PaymentRateApplicationDetails>) {
      id == 123L
      type == PAYMENT_RATE
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        paymentRate == 6.0
        fulfillmentDate == LocalDate.parse("2022-01-01")
        cancellationDeadline == Instant.parse("2021-11-30T21:59:59.999999999Z")
      }
    }
    applications.size() == 8
  }

  def "gets payment rate applications"() {
    given:
        def person = samplePerson()
        def transferApplication1 = sampleTransferApplicationDto()
        def completedTransferApplication = sampleTransferApplicationDto()
        completedTransferApplication.status = COMPLETE
        completedTransferApplication.id = 456L
        def transferApplication2 = sampleTransferApplicationDto()
        def withdrawalApplication = sampleWithdrawalApplicationDto()
        def earlyWithdrawalApplication = sampleEarlyWithdrawalApplicationDto()
        def pikTransferApplication = samplePikTransferApplicationDto()
        def paymentRateApplication = samplePendingPaymentRateApplicationDto()

        episService.getApplications(person) >> [
            transferApplication1, transferApplication2, completedTransferApplication,
            pikTransferApplication, withdrawalApplication, earlyWithdrawalApplication,
            paymentRateApplication
        ]
        localeService.getCurrentLocale() >> Locale.ENGLISH
        fundRepository.findByIsin("source") >> sampleFunds().first()
        fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

        mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
        paymentApplicationService.getPaymentApplications(person) >> [paymentApplication().build()]

    when:
        def applications = applicationService.getPaymentRateApplications(person)

    then:
        with(applications.first() as Application<PaymentRateApplicationDetails>) {
          id == 123L
          type == PAYMENT_RATE
          status == PENDING
          creationTime == TestClockHolder.now
          with(details) {
            paymentRate == 6.0
            fulfillmentDate == LocalDate.parse("2022-01-01")
            cancellationDeadline == Instant.parse("2021-11-30T21:59:59.999999999Z")
          }
        }
        applications.size() == 1
  }


  def "gets withdrawals applications"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()


    def fundPensionOpening = sampleFundPensionOpeningApplicationDto()
    def fundPensionOpeningThirdPillar = sampleThirdPillarFundPensionOpeningApplicationDto()
    def partialWithdrawal = samplePartialWithdrawalApplicationDto()
    def thirdPillarWithdrawal = sampleThirdPillarWithdrawalApplicationDto()

    episService.getApplications(person) >> [
        fundPensionOpening, fundPensionOpeningThirdPillar, partialWithdrawal, thirdPillarWithdrawal
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []
    savingFundPaymentService.getPendingPaymentsForUser(_) >> []
    savingFundRedemptionService.getPendingRedemptionsForUser(_) >> []

    when:
    def applications = applicationService.getAllApplications(person)

    then:
    with(applications[0] as Application<FundPensionOpeningApplicationDetails>) {
      id == 123L
      type == FUND_PENSION_OPENING
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        fulfillmentDate == LocalDate.parse("2021-04-20")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
        depositAccountIBAN == "EE_TEST_IBAN"
        details.getPillar() == 2
        with(fundPensionDetails) {
          durationYears() == 20
          paymentsPerYear() == 12
        }
      }
    }

    with(applications[1] as Application<FundPensionOpeningApplicationDetails>) {
      id == 123L
      type == FUND_PENSION_OPENING_THIRD_PILLAR
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        fulfillmentDate == LocalDate.parse("2021-04-20")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
        depositAccountIBAN == "EE_TEST_IBAN"
        details.getPillar() == 3
        with(fundPensionDetails) {
          durationYears() == 20
          paymentsPerYear() == 12
        }
      }
    }

    with(applications[2] as Application<WithdrawalApplicationDetails>) {
      id == 123L
      type == PARTIAL_WITHDRAWAL
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        fulfillmentDate == LocalDate.parse("2021-04-20")
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.999999999Z")
        depositAccountIBAN == "EE_TEST_IBAN"
        details.getPillar() == 2
      }
    }

    with(applications[3] as Application<WithdrawalApplicationDetails>) {
      id == 123L
      type == WITHDRAWAL_THIRD_PILLAR
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        fulfillmentDate == LocalDate.parse("2021-03-17")
        cancellationDeadline == Instant.parse("2021-03-11T10:00:00Z")
        depositAccountIBAN == "EE_TEST_IBAN"
        details.getPillar() == 3
      }
    }
    applications.size() == 4
  }


  def "get has pending withdrawals by pillar"() {
    given:
    def person = samplePerson()


    def fundPensionOpening = sampleFundPensionOpeningApplicationDto()
    def fundPensionOpeningThirdPillar = sampleThirdPillarFundPensionOpeningApplicationDto()
    def partialWithdrawal = samplePartialWithdrawalApplicationDto()
    def thirdPillarWithdrawal = sampleThirdPillarWithdrawalApplicationDto()

    episService.getApplications(person) >> [
        fundPensionOpening, fundPensionOpeningThirdPillar, partialWithdrawal, thirdPillarWithdrawal
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []

    when:
    def hasPendingSecondPillarWithdrawals = applicationService.hasPendingWithdrawals(person, SECOND)
    def hasPendingThirdPillarWithdrawals = applicationService.hasPendingWithdrawals(person, THIRD)

    then:
    hasPendingSecondPillarWithdrawals
    hasPendingThirdPillarWithdrawals
  }

  def "gets saving fund payment applications for authenticated person"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    // Create sample UUIDs for the payments
    def payment1Id = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    def payment2Id = UUID.fromString("87654321-4321-4321-4321-cba987654321")

    // Create sample saving fund payments
    def payment1 = Mock(SavingFundPayment) {
      getId() >> payment1Id
      getAmount() >> 100.0
      getCurrency() >> EUR
      getCreatedAt() >> TestClockHolder.now
      getStatus() >> SavingFundPayment.Status.CREATED
    }

    def payment2 = Mock(SavingFundPayment) {
      getId() >> payment2Id
      getAmount() >> 250.0
      getCurrency() >> EUR
      getCreatedAt() >> TestClockHolder.now.minusSeconds(3600)
      getStatus() >> SavingFundPayment.Status.VERIFIED
    }

    episService.getApplications(authenticatedPerson) >> []
    localeService.getCurrentLocale() >> Locale.ENGLISH
    paymentApplicationService.getPaymentApplications(authenticatedPerson) >> []
    savingFundPaymentService.getPendingPaymentsForUser(authenticatedPerson.getUserId()) >> [payment1, payment2]
    savingFundRedemptionService.getPendingRedemptionsForUser(authenticatedPerson.getUserId()) >> []

    savingFundPaymentDeadlinesService.getCancellationDeadline(payment1) >> Instant.parse("2021-03-31T21:00:00.000000000Z")
    savingFundPaymentDeadlinesService.getFulfillmentDeadline(payment1) >> Instant.parse("2021-04-20T10:00:00Z")
    savingFundPaymentDeadlinesService.getCancellationDeadline(payment2) >> Instant.parse("2021-03-31T21:00:00.000000000Z")
    savingFundPaymentDeadlinesService.getFulfillmentDeadline(payment2) >> Instant.parse("2021-04-20T10:00:00Z")

    when:
    def applications = applicationService.getAllApplications(authenticatedPerson)

    then:
    applications.size() == 2

    with(applications[0] as Application<SavingFundPaymentApplicationDetails>) {
      id == payment2Id.getMostSignificantBits() // Converted from UUID
      status == PENDING // TODO: Map real status when available
      creationTime == TestClockHolder.now.minusSeconds(3600)
      with(details) {
        amount == 250.0
        currency == EUR
        paymentId == payment2Id
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.000000000Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }

    with(applications[1] as Application<SavingFundPaymentApplicationDetails>) {
      id == payment1Id.getMostSignificantBits() // Converted from UUID
      status == PENDING // TODO: Map real status when available
      creationTime == TestClockHolder.now
      with(details) {
        amount == 100.0
        currency == EUR
        paymentId == payment1Id
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59.000000000Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }
  }

  def "gets saving fund redemption applications for authenticated person"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

    def redemption1Id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    def redemption2Id = UUID.fromString("22222222-2222-2222-2222-222222222222")

    def redemption1 = RedemptionRequest.builder()
        .id(redemption1Id)
        .userId(authenticatedPerson.getUserId())
        .fundUnits(valueOf(10.12345))
        .requestedAmount(valueOf(150.00))
        .customerIban("EE123456789012345678")
        .status(RESERVED)
        .requestedAt(TestClockHolder.now)
        .build()

    def redemption2 = RedemptionRequest.builder()
        .id(redemption2Id)
        .userId(authenticatedPerson.getUserId())
        .fundUnits(valueOf(20.54321))
        .requestedAmount(valueOf(300.50))
        .customerIban("EE987654321098765432")
        .status(VERIFIED)
        .requestedAt(TestClockHolder.now.minusSeconds(7200))
        .build()

    episService.getApplications(authenticatedPerson) >> []
    localeService.getCurrentLocale() >> Locale.ENGLISH
    paymentApplicationService.getPaymentApplications(authenticatedPerson) >> []
    savingFundPaymentService.getPendingPaymentsForUser(authenticatedPerson.getUserId()) >> []
    savingFundRedemptionService.getPendingRedemptionsForUser(authenticatedPerson.getUserId()) >> [redemption1, redemption2]

    savingFundPaymentDeadlinesService.getCancellationDeadline(_ as Instant) >> Instant.parse("2021-03-31T21:00:00Z")
    savingFundPaymentDeadlinesService.getFulfillmentDeadline(_ as Instant) >> Instant.parse("2021-04-20T10:00:00Z")

    when:
    def applications = applicationService.getAllApplications(authenticatedPerson)

    then:
    applications.size() == 2

    with(applications[0] as Application<SavingFundWithdrawalApplicationDetails>) {
      id == redemption2Id.getMostSignificantBits()
      status == PENDING
      creationTime == TestClockHolder.now.minusSeconds(7200)
      with(details) {
        id == redemption2Id
        amount == valueOf(300.50)
        currency == EUR
        iban == "EE987654321098765432"
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }

    with(applications[1] as Application<SavingFundWithdrawalApplicationDetails>) {
      id == redemption1Id.getMostSignificantBits()
      status == PENDING
      creationTime == TestClockHolder.now
      with(details) {
        id == redemption1Id
        amount == valueOf(150.00)
        currency == EUR
        iban == "EE123456789012345678"
        cancellationDeadline == Instant.parse("2021-03-31T20:59:59Z")
        fulfillmentDeadline == Instant.parse("2021-04-20T10:00:00Z")
      }
    }
  }
}
