package ee.tuleva.onboarding.mandate.application

import ee.tuleva.onboarding.account.CashFlowService
import ee.tuleva.onboarding.epis.cashflows.CashFlow
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.locale.MockLocaleService
import ee.tuleva.onboarding.payment.PaymentService
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE
import static ee.tuleva.onboarding.fund.ApiFundResponseFixture.tuleva3rdPillarApiFundResponse
import static ee.tuleva.onboarding.fund.FundFixture.tuleva3rdPillarFund
import static ee.tuleva.onboarding.mandate.application.PaymentApplicationService.*
import static ee.tuleva.onboarding.payment.PaymentFixture.aPendingPayment
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
    def payment = aPendingPayment()
    paymentService.getPayments(person, PENDING) >> [payment]
    cashFlowService.getCashFlowStatement(person) >> CashFlowStatement.builder()
        .transactions([
            new CashFlow(null, Instant.parse("2018-12-31T00:00:00Z"), payment.amount, "EUR", CASH),
            new CashFlow(null, Instant.parse("2018-12-31T00:00:00Z"), -payment.amount, "EUR", CASH),
        ])
        .build()
    fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN) >> tuleva3rdPillarFund

    when:
    def paymentApplications = paymentApplicationService.getPaymentApplications(person)

    then:
    paymentApplications.size() == 1
    with(paymentApplications[0]) {
      id == 123L
      creationTime == payment.createdTime
      status == COMPLETE
      with(details) {
        amount == 10.0
        currency == EUR
        targetFund == tuleva3rdPillarApiFundResponse()
      }
    }
  }
}
