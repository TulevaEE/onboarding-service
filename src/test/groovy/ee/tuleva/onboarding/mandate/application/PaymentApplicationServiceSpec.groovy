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

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.TULEVA_3RD_PILLAR_FUND_ISIN
import static ee.tuleva.onboarding.payment.PaymentFixture.aPendingPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.contributionAmountHigh
import static ee.tuleva.onboarding.payment.PaymentFixture.contributionAmountLow
import static ee.tuleva.onboarding.payment.PaymentFixture.pendingPaymentAmount
import static ee.tuleva.onboarding.payment.PaymentStatus.PENDING

class PaymentApplicationServiceSpec extends Specification {

  PaymentService paymentService = Mock()
  CashFlowService cashFlowService = Mock()
  FundRepository fundRepository = Mock()
  LocaleService localeService = new MockLocaleService()
  PaymentApplicationService paymentApplicationService =
      new PaymentApplicationService(paymentService, cashFlowService, fundRepository, localeService)

  def "can get payment applications"() {
    given:
    def person = samplePerson()
    paymentService.getPayments(person, PENDING) >> pendingPayments
    cashFlowService.getCashFlowStatement(person) >> CashFlowStatement.builder()
        .transactions(transactions)
        .build()
    fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN) >> tuleva3rdPillarFund

    when:
    def paymentApplications = paymentApplicationService.getPaymentApplications(person)

    then:
    paymentApplications == pendingPaymentApplications

    where:
    transactions                                                                                                                     | pendingPayments                                | pendingPaymentApplications
    [transaction(), negativeTransaction()]                                                                                           | [aPendingPayment()]                            | [pendingPayment()]
    []                                                                                                                               | [aPendingPayment()]                            | [pendingPayment()]
    [transaction(), negativeTransaction(), tulevaContributionHigh()]                                                                 | [aPendingPayment()]                            | [completePayment()]
    [transaction(), negativeTransaction(), tulevaContributionLow()]                                                                  | [aPendingPayment()]                            | [completePayment()]
    [transaction(), negativeTransaction(), foreignContribution()]                                                                    | [aPendingPayment()]                            | [pendingPayment()]
    []                                                                                                                               | [aPendingPayment(456L), aPendingPayment(123L)] | [pendingPayment(123L), pendingPayment(456L)]
    [transaction()]                                                                                                                  | [aPendingPayment(456L), aPendingPayment(123L)] | [pendingPayment(123L), pendingPayment(456L)]
    [transaction(), transaction()]                                                                                                   | [aPendingPayment(456L), aPendingPayment(123L)] | [pendingPayment(123L), pendingPayment(456L)]
    [transaction(), transaction(), negativeTransaction()]                                                                            | [aPendingPayment(456L), aPendingPayment(123L)] | [pendingPayment(123L), pendingPayment(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh()]                                                  | [aPendingPayment(456L), aPendingPayment(123L)] | [completePayment(123L), pendingPayment(456L)]
    [transaction(), transaction(), negativeTransaction(), tulevaContributionHigh(), negativeTransaction(), tulevaContributionHigh()] | [aPendingPayment(456L), aPendingPayment(123L)] | [completePayment(123L), completePayment(456L)]
  }

  private CashFlow transaction() {
    return new CashFlow(null, Instant.parse("2022-09-29T10:25:30Z"), null, pendingPaymentAmount, "EUR", CASH)
  }

  private CashFlow negativeTransaction() {
    return new CashFlow(null, Instant.parse("2022-09-29T10:35:30Z"), null, -pendingPaymentAmount, "EUR", CASH)
  }

  private CashFlow tulevaContributionHigh() {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, Instant.parse("2022-09-29T10:35:30Z"), Instant.parse("2022-09-28T00:00:00Z"), contributionAmountHigh, "EUR", CONTRIBUTION_CASH)
  }

  private CashFlow tulevaContributionLow() {
    return new CashFlow(TULEVA_3RD_PILLAR_FUND_ISIN, Instant.parse("2022-09-29T10:45:30Z"), Instant.parse("2022-09-28T00:00:00Z"), contributionAmountLow, "EUR", CONTRIBUTION_CASH)
  }

  private CashFlow foreignContribution() {
    return new CashFlow("OTHERISIN", Instant.parse("2022-09-29T10:45:30Z"), Instant.parse("2022-09-28T00:00:00Z"), contributionAmountHigh, "EUR", CONTRIBUTION_CASH)
  }

  private Application<PaymentApplicationDetails> completePayment(Long id = 123L) {
    return pendingPayment(id, COMPLETE)
  }

  private Application<PaymentApplicationDetails> pendingPayment(Long id = 123L, ApplicationStatus status = ApplicationStatus.PENDING) {
    return new Application<PaymentApplicationDetails>(
        id, Instant.parse("2022-09-29T10:15:30Z"), status,
        new PaymentApplicationDetails(
            pendingPaymentAmount, EUR, tuleva3rdPillarApiFundResponse(), ApplicationType.PAYMENT,
        )
    )
  }
}
