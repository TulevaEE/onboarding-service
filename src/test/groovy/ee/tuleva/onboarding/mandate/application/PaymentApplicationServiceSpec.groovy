package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentService
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.REFUND
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.FAILED
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.PaymentFixture.aPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.contributionAmountHigh
import static ee.tuleva.onboarding.payment.PaymentFixture.contributionAmountLow
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentAmount
import static java.time.temporal.ChronoUnit.DAYS

class PaymentApplicationServiceSpec extends Specification {

  static final Duration threeDays = Duration.ofDays(3)
  static final Duration tenMinutes = Duration.ofMinutes(10)
  static final defaultPriceTime = Instant.parse("2022-09-28T00:00:00Z")
  static final defaultPaymentTime = Instant.parse("2022-09-29T10:15:30Z")
  static final defaultTransactionTime = defaultPaymentTime + tenMinutes
  static final defaultNegativeTransactionTime = defaultTransactionTime + tenMinutes
  static final defaultRefundTransactionTime = defaultNegativeTransactionTime + tenMinutes
  static final defaultContributionTime = defaultRefundTransactionTime + tenMinutes

  PaymentService paymentService = Mock()
  CashFlowService cashFlowService = Mock()
  FundRepository fundRepository = Mock()
  LocaleService localeService = new MockLocaleService()
  PaymentApplicationService paymentApplicationService =
      new PaymentApplicationService(paymentService, cashFlowService, fundRepository, localeService)

  def "can get payment applications"() {
    given:
    def person = samplePerson()
    paymentService.getPayments(person) >> payments
    cashFlowService.getCashFlowStatement(person) >> CashFlowStatement.builder()
        .transactions(transactions)
        .build()
    fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN) >> tuleva3rdPillarFund

    when:
    def paymentApplications = paymentApplicationService.getPaymentApplications(person)

    then:
    paymentApplications == pendingPaymentApplications

    where:
    transactions                                                                                                                                                                    | payments                         | pendingPaymentApplications
    [transaction(), negativeTransaction()]                                                                                                                                          | [aPayment()]                     | [aPendingPaymentApplication()]
    [transaction(), refundTransaction()]                                                                                                                                            | [aPayment()]                     | [aFailedPaymentApplication()]
    []                                                                                                                                                                              | [aPayment()]                     | [aPendingPaymentApplication()]
    [transaction(), negativeTransaction(), tulevaContributionHigh()]                                                                                                                | [aPayment()]                     | [aCompletePaymentApplication()]
    [transaction(), negativeTransaction(), tulevaContributionLow()]                                                                                                                 | [aPayment()]                     | [aCompletePaymentApplication()]
    [transaction(), negativeTransaction(), foreignContribution()]                                                                                                                   | [aPayment()]                     | [aPendingPaymentApplication()]
    []                                                                                                                                                                              | [aPayment(456L), aPayment(123L)] | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction()]                                                                                                                                                                 | [aPayment(456L), aPayment(123L)] | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction()]                                                                                                                                                  | [aPayment(456L), aPayment(123L)] | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction()]                                                                                                                           | [aPayment(456L), aPayment(123L)] | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh()]                                                                                                 | [aPayment(456L), aPayment(123L)] | [aCompletePaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh(), negativeTransaction(), tulevaContributionHigh()]                                                | [aPayment(456L), aPayment(123L)] | [aCompletePaymentApplication(123L), aCompletePaymentApplication(456L)]
    and: 'Does not link with cash flows newer than 3 days'
    [transaction(defaultTransactionTime + threeDays), negativeTransaction(defaultNegativeTransactionTime + threeDays), tulevaContributionHigh(defaultContributionTime + threeDays)] | [aPayment()]                     | [aPendingPaymentApplication()]
  }

  private CashFlow transaction(Instant createdTime = defaultTransactionTime) {
    return new CashFlow(null, createdTime, null, aPaymentAmount, "EUR", CASH)
  }

  private CashFlow negativeTransaction(Instant createdTime = defaultNegativeTransactionTime) {
    return new CashFlow(null, createdTime, null, -aPaymentAmount, "EUR", CASH)
  }

  private CashFlow refundTransaction(Instant createdTime = defaultRefundTransactionTime) {
    return new CashFlow(null, createdTime, null, -aPaymentAmount, "EUR", REFUND)
  }

  private CashFlow tulevaContributionHigh(Instant createdTime = defaultContributionTime) {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, createdTime, defaultPriceTime, contributionAmountHigh, "EUR", CONTRIBUTION_CASH)
  }

  private CashFlow tulevaContributionLow(Instant createdTime = defaultContributionTime) {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, createdTime, defaultPriceTime, contributionAmountLow, "EUR", CONTRIBUTION_CASH)
  }

  private CashFlow foreignContribution(Instant createdTime = defaultContributionTime) {
    return new CashFlow("OTHERISIN", createdTime, defaultPriceTime, contributionAmountHigh, "EUR", CONTRIBUTION_CASH)
  }

  private Application<PaymentApplicationDetails> aCompletePaymentApplication(Long id = 123L) {
    return aPendingPaymentApplication(id, COMPLETE)
  }

  private Application<PaymentApplicationDetails> aFailedPaymentApplication(Long id = 123L) {
    return aPendingPaymentApplication(id, FAILED)
  }

  private Application<PaymentApplicationDetails> aPendingPaymentApplication(Long id = 123L, ApplicationStatus status = ApplicationStatus.PENDING, Instant createdTime = defaultPaymentTime) {
    return new Application<PaymentApplicationDetails>(
        id, createdTime, status,
        new PaymentApplicationDetails(
            aPaymentAmount, EUR, tuleva3rdPillarApiFundResponse(), ApplicationType.PAYMENT,
        )
    )
  }
}
