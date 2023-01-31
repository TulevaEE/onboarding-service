package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentService
import ee.tuleva.onboarding.time.TestClockHolder
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.*
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.FAILED
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.PaymentFixture.*

class PaymentApplicationServiceSpec extends Specification {

  static final Duration fiveDays = Duration.ofDays(7)
  static final Duration tenMinutes = Duration.ofMinutes(10)
  static final defaultPriceTime = aPaymentCreationTime - Duration.ofMinutes(15)
  static final defaultPaymentTime = aPaymentCreationTime
  static final defaultTransactionTime = defaultPaymentTime + tenMinutes
  static final defaultNegativeTransactionTime = defaultTransactionTime + tenMinutes
  static final defaultRefundTransactionTime = defaultNegativeTransactionTime + tenMinutes
  static final defaultContributionTime = defaultRefundTransactionTime + tenMinutes

  PaymentService paymentService = Mock()
  CashFlowService cashFlowService = Mock()
  FundRepository fundRepository = Mock()
  PublicHolidays publicHolidays = new PublicHolidays(TestClockHolder.clock)
  LocaleService localeService = new MockLocaleService()
  PaymentApplicationService paymentApplicationService =
      new PaymentApplicationService(paymentService, cashFlowService, fundRepository, localeService, TestClockHolder.clock, publicHolidays)

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
    transactions                                                                                                                     | payments                                            | pendingPaymentApplications
    [transaction(), negativeTransaction()]                                                                                           | [aPayment()]                                        | [aPendingPaymentApplication()]
    [transaction(), refundTransaction()]                                                                                             | [aPayment()]                                        | [aFailedPaymentApplication()]
    []                                                                                                                               | [aPayment()]                                        | [aPendingPaymentApplication()]
    [transaction(), negativeTransaction(), tulevaContributionHigh()]                                                                 | [aPayment()]                                        | [aCompletePaymentApplication()]
    [transaction(), negativeTransaction(), tulevaContributionLow()]                                                                  | [aPayment()]                                        | [aCompletePaymentApplication()]
    [transaction(), negativeTransaction(), foreignContribution()]                                                                    | [aPayment()]                                        | [aPendingPaymentApplication()]
    []                                                                                                                               | [aPayment(456L), aPayment(123L)]                    | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction()]                                                                                                                  | [aPayment(456L), aPayment(123L)]                    | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction()]                                                                                                   | [aPayment(456L), aPayment(123L)]                    | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction()]                                                                            | [aPayment(456L), aPayment(123L)]                    | [aPendingPaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh()]                                                  | [aPayment(456L), aPayment(123L)]                    | [aCompletePaymentApplication(123L), aPendingPaymentApplication(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh(), negativeTransaction(), tulevaContributionHigh()] | [aPayment(456L), aPayment(123L)]                    | [aCompletePaymentApplication(123L), aCompletePaymentApplication(456L)]
    and: 'Does not link with cash flows newer than 3 working days (five days), shows as failed'
    [transaction()]                                                                                                                  | [aPayment(123L, defaultTransactionTime - fiveDays)] | [aFailedPaymentApplication(123L, defaultTransactionTime - fiveDays)]
    and: 'If no transaction in three working days (five days), mark as failed'
    []                                                                                                                               | [aPayment(123L, defaultTransactionTime - fiveDays)] | [aFailedPaymentApplication(123L, defaultTransactionTime - fiveDays)]
  }

  private CashFlow transaction(Instant createdTime = defaultTransactionTime) {
    return new CashFlow(null, createdTime, null, aPaymentAmount, EUR, CASH, null)
  }

  private CashFlow negativeTransaction(Instant createdTime = defaultNegativeTransactionTime) {
    return new CashFlow(null, createdTime, null, -aPaymentAmount, EUR, CASH, null)
  }

  private CashFlow refundTransaction(Instant createdTime = defaultRefundTransactionTime) {
    return new CashFlow(null, createdTime, null, -aPaymentAmount, EUR, REFUND, null)
  }

  private CashFlow tulevaContributionHigh(Instant createdTime = defaultContributionTime) {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, createdTime, defaultPriceTime, contributionAmountHigh, EUR, CONTRIBUTION_CASH, null)
  }

  private CashFlow tulevaContributionLow(Instant createdTime = defaultContributionTime) {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, createdTime, defaultPriceTime, contributionAmountLow, EUR, CONTRIBUTION_CASH, null)
  }

  private CashFlow foreignContribution(Instant createdTime = defaultContributionTime) {
    return new CashFlow("OTHERISIN", createdTime, defaultPriceTime, contributionAmountHigh, EUR, CONTRIBUTION_CASH, null)
  }

  private Application<PaymentApplicationDetails> aCompletePaymentApplication(Long id = 123L, Instant createdTime = defaultPaymentTime) {
    return aPendingPaymentApplication(id, COMPLETE, createdTime)
  }

  private Application<PaymentApplicationDetails> aFailedPaymentApplication(Long id = 123L, Instant createdTime = defaultPaymentTime) {
    return aPendingPaymentApplication(id, FAILED, createdTime)
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
