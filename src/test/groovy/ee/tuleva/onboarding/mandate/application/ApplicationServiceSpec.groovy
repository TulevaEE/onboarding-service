package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.application.PaymentApplicationDetails
import ee.tuleva.onboarding.payment.application.PaymentLinkingService
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentDeadlinesService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

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

class ApplicationServiceSpec extends Specification {

  EpisService episService = Mock()
  LocaleService localeService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()
  PaymentLinkingService paymentApplicationService = Mock()
  SavingFundPaymentDeadlinesService savingFundPaymentDeadlinesService = Mock()

  ApplicationService applicationService =
      new ApplicationService(episService, localeService, fundRepository, mandateDeadlinesService, paymentApplicationService, savingFundPaymentDeadlinesService)

  def "gets applications"() {
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

  // TODO: Add test for SavingFundPaymentApplications once we can fetch payments
}
