package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.applicationtype.ApplicationType
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.deadline.MandateDeadlines
import ee.tuleva.onboarding.deadline.MandateDeadlinesService
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.error.NotFoundException
import ee.tuleva.onboarding.mandate.MandateGateway
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.payment.application.PaymentApplicationDetails
import ee.tuleva.onboarding.savings.fund.application.SavingFundPaymentApplicationDetails
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.deadline.MandateDeadlinesFixture.sampleDeadlines
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFunds
import static ee.tuleva.onboarding.mandate.application.ApplicationSnapshotFixture.*
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.paymentApplication
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.FAILED
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING
import static ee.tuleva.onboarding.applicationtype.ApplicationType.*
import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.pillar.Pillar.THIRD

class ApplicationServiceSpec extends Specification {

  MandateGateway mandateGateway = Mock()
  LocaleService localeService = Mock()
  FundRepository fundRepository = Mock()
  MandateDeadlinesService mandateDeadlinesService = Mock()
  PaymentApplications paymentApplicationService = Mock()
  SavingsApplications savingsApplications = Mock()
  RejoinCancellations rejoinCancellations = Mock()

  ApplicationService applicationService =
      new ApplicationService(mandateGateway, localeService, fundRepository, mandateDeadlinesService, paymentApplicationService, savingsApplications, rejoinCancellations)

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

    mandateGateway.getApplications(person) >> [
        transferApplication1, transferApplication2, completedTransferApplication,
        pikTransferApplication, withdrawalApplication, earlyWithdrawalApplication,
        paymentRateApplication
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> [paymentApplication().build()]
    savingsApplications.getApplications(person) >> []

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
          targetPik == "EE471000001020145685"
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

        mandateGateway.getApplications(person) >> [
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


  def "lists a pending rejoin the person has not canceled through us, with the deadline to cancel it and the date contributions start, both from the day it was filed"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def rejoinApplication = samplePendingRejoinApplicationDto()
    def estonianClock = Clock.fixed(Instant.parse("2020-06-01T10:00:00Z"), ZoneId.of("Europe/Tallinn"))
    mandateGateway.getApplications(person) >> [rejoinApplication]
    mandateDeadlinesService.getDeadlines(rejoinApplication.date) >>
        new MandateDeadlines(estonianClock, new PublicHolidays(), rejoinApplication.date)
    rejoinCancellations.isCanceled(person, rejoinApplication) >> false
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    def applications = applicationService.getAllApplications(person)

    then:
    applications == [
        Application.<RejoinApplicationDetails> builder()
            .id(123L)
            .creationTime(TestClockHolder.now)
            .status(PENDING)
            .details(new RejoinApplicationDetails(
                Instant.parse("2020-03-31T20:59:59.999999999Z"), LocalDate.parse("2020-09-01")))
            .build()
    ]
  }

  def "hides a rejoin the person has canceled through us, which Pensionikeskus may still list as pending"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def rejoinApplication = samplePendingRejoinApplicationDto()
    mandateGateway.getApplications(person) >> [rejoinApplication]
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    rejoinCancellations.isCanceled(person, rejoinApplication) >> true
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    def applications = applicationService.getAllApplications(person)

    then:
    applications == []
  }

  @Unroll
  def "lists a #status rejoin even when the person canceled it through us, since only a pending one can be withdrawn"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def rejoinApplication = samplePendingRejoinApplicationDto()
    rejoinApplication.status = status
    def deadlines = sampleDeadlines()
    mandateGateway.getApplications(person) >> [rejoinApplication]
    mandateDeadlinesService.getDeadlines(rejoinApplication.date) >> deadlines
    rejoinCancellations.isCanceled(person, rejoinApplication) >> true
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    def applications = applicationService.getAllApplications(person)

    then:
    applications == [
        Application.<RejoinApplicationDetails> builder()
            .id(123L)
            .creationTime(TestClockHolder.now)
            .status(status)
            .details(new RejoinApplicationDetails(
                deadlines.getCancellationDeadline(REJOIN), deadlines.getFulfillmentDate(REJOIN)))
            .build()
    ]

    where:
    status << [COMPLETE, FAILED]
  }

  @Unroll
  def "has a pending rejoin only while Pensionikeskus lists a rejoin as pending: #status"() {
    given:
    def person = samplePerson()
    def rejoinApplication = samplePendingRejoinApplicationDto()
    rejoinApplication.status = status
    mandateGateway.getApplications(person) >> [rejoinApplication, sampleTransferApplicationDto()]

    expect:
    applicationService.hasPendingRejoin(person) == pendingRejoin

    where:
    status   || pendingRejoin
    PENDING  || true
    COMPLETE || false
    FAILED   || false
  }

  def "a rejoin canceled through us still counts as pending, so a leaver who has just changed their mind is not asked to rejoin again"() {
    given:
    def person = samplePerson()
    mandateGateway.getApplications(person) >> [samplePendingRejoinApplicationDto()]

    when:
    def pendingRejoin = applicationService.hasPendingRejoin(person)

    then:
    pendingRejoin
    0 * rejoinCancellations.isCanceled(_, _)
  }

  def "a pending application of another type is no pending rejoin"() {
    given:
    def person = samplePerson()
    mandateGateway.getApplications(person) >> [sampleTransferApplicationDto()]

    expect:
    !applicationService.hasPendingRejoin(person)
  }

  def "gets withdrawals applications"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()


    def fundPensionOpening = sampleFundPensionOpeningApplicationDto()
    def fundPensionOpeningThirdPillar = sampleThirdPillarFundPensionOpeningApplicationDto()
    def partialWithdrawal = samplePartialWithdrawalApplicationDto()
    def thirdPillarWithdrawal = sampleThirdPillarWithdrawalApplicationDto()

    mandateGateway.getApplications(person) >> [
        fundPensionOpening, fundPensionOpeningThirdPillar, partialWithdrawal, thirdPillarWithdrawal
    ]
    localeService.getCurrentLocale() >> Locale.ENGLISH

    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

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


  def "a person acting for a child gets the child's pillar applications alongside savings applications"() {
    given:
    def personActingForChild = sampleAuthenticatedPersonAndMember()
        .role(new Role(PERSON, "61506150006", "Child Name"))
        .build()
    def childPayment = paymentApplication().creationTime(TestClockHolder.now + Duration.ofDays(1)).build()
    def childSavingsPayment = savingFundPaymentApplication(2L, TestClockHolder.now + Duration.ofDays(2))
    mandateGateway.getApplications(personActingForChild) >> [sampleThirdPillarWithdrawalApplicationDto()]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(personActingForChild) >> [childPayment]
    savingsApplications.getApplications(personActingForChild) >> [childSavingsPayment]

    when:
    def applications = applicationService.getAllApplications(personActingForChild)

    then:
    with(applications[0] as Application<WithdrawalApplicationDetails>) {
      id == 123L
      type == WITHDRAWAL_THIRD_PILLAR
      status == PENDING
      creationTime == TestClockHolder.now
    }
    applications.drop(1) == [childPayment, childSavingsPayment]
  }

  def "a company role does not fall back to the representative's own pillar applications"() {
    given:
    def personActingForCompany = sampleAuthenticatedPersonLegalEntity().build()
    def laterSavingsPayment = savingFundPaymentApplication(2L, TestClockHolder.now)
    def earlierSavingsPayment = savingFundPaymentApplication(1L, TestClockHolder.now - Duration.ofDays(1))
    savingsApplications.getApplications(personActingForCompany) >> [laterSavingsPayment, earlierSavingsPayment]

    when:
    def applications = applicationService.getAllApplications(personActingForCompany)

    then:
    applications == [earlierSavingsPayment, laterSavingsPayment]
    0 * mandateGateway.getApplications(_)
    0 * paymentApplicationService.getPaymentApplications(_)
  }

  private static Application<SavingFundPaymentApplicationDetails> savingFundPaymentApplication(Long id, Instant creationTime) {
    return Application.<SavingFundPaymentApplicationDetails> builder()
        .id(id)
        .creationTime(creationTime)
        .status(PENDING)
        .details(SavingFundPaymentApplicationDetails.builder()
            .amount(100.00)
            .currency(EUR)
            .paymentId(UUID.fromString("3f6c1b0e-9a3d-4c2e-8f4b-1d2a3b4c5d6e"))
            .fulfillmentDeadline(creationTime + Duration.ofDays(2))
            .build())
        .build()
  }

  def "gets a single application by id"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def transferApplication1 = sampleTransferApplicationDto()
    def completedTransferApplication = sampleTransferApplicationDto()
    completedTransferApplication.status = COMPLETE
    completedTransferApplication.id = 456L

    mandateGateway.getApplications(person) >> [transferApplication1, completedTransferApplication]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    def application = applicationService.getApplication(456L, person)

    then:
    application.id == 456L
    application.status == COMPLETE
  }

  def "throws NotFoundException when no application matches the given id"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def transferApplication1 = sampleTransferApplicationDto()

    mandateGateway.getApplications(person) >> [transferApplication1]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    applicationService.getApplication(999999L, person)

    then:
    thrown(NotFoundException)
  }

  def "getApplications and getTransferApplications filter by status"() {
    given:
    def person = sampleAuthenticatedPersonAndMember().build()
    def transferApplication1 = sampleTransferApplicationDto()
    def completedTransferApplication = sampleTransferApplicationDto()
    completedTransferApplication.status = COMPLETE
    completedTransferApplication.id = 456L

    mandateGateway.getApplications(person) >> [transferApplication1, completedTransferApplication]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    fundRepository.findByIsin("source") >> sampleFunds().first()
    fundRepository.findByIsin("target") >> sampleFunds().drop(1).first()
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []
    savingsApplications.getApplications(person) >> []

    when:
    def pendingApplications = applicationService.getApplications(PENDING, person)
    def completedApplications = applicationService.getApplications(COMPLETE, person)
    def pendingTransfers = applicationService.getTransferApplications(PENDING, person)

    then:
    pendingApplications*.id == [123L]
    completedApplications*.id == [456L]
    pendingTransfers*.id == [123L]
  }

  def "get has pending withdrawals by pillar"() {
    given:
    def person = samplePerson()


    def fundPensionOpening = sampleFundPensionOpeningApplicationDto()
    def fundPensionOpeningThirdPillar = sampleThirdPillarFundPensionOpeningApplicationDto()
    def partialWithdrawal = samplePartialWithdrawalApplicationDto()
    def thirdPillarWithdrawal = sampleThirdPillarWithdrawalApplicationDto()

    mandateGateway.getApplications(person) >> [
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

  def "has pending withdrawals is false when no pending withdrawal matches the pillar"() {
    given:
    def person = samplePerson()
    def partialWithdrawal = samplePartialWithdrawalApplicationDto()

    mandateGateway.getApplications(person) >> [partialWithdrawal]
    localeService.getCurrentLocale() >> Locale.ENGLISH
    mandateDeadlinesService.getDeadlines(_ as Instant) >> sampleDeadlines()
    paymentApplicationService.getPaymentApplications(person) >> []

    expect:
    applicationService.hasPendingWithdrawals(person, SECOND)
    !applicationService.hasPendingWithdrawals(person, THIRD)
  }
}
