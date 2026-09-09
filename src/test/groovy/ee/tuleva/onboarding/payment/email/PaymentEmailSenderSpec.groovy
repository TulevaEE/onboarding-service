package ee.tuleva.onboarding.payment.email

import ee.tuleva.onboarding.auth.SecurityContextRunner
import ee.tuleva.onboarding.auth.principal.MinorCannotSelfAuthenticateException
import ee.tuleva.onboarding.nudge.NudgeAccount
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.nudge.NudgeDecisionService
import ee.tuleva.onboarding.party.PartyId
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static ee.tuleva.onboarding.nudge.NudgeContext.SAVINGS_FUND_PAYMENT
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT
import static ee.tuleva.onboarding.nudge.NudgeKey.ACCOUNT_RECURRING
import static ee.tuleva.onboarding.nudge.NudgeKey.MEMBERSHIP
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static java.util.Locale.ENGLISH

class PaymentEmailSenderSpec extends Specification {

  static final UUID CHILD_LINK_ID = UUID.fromString("33333333-3333-3333-3333-333333333333")
  static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444")

  PaymentEmailService paymentEmailService = Mock()
  SecurityContextRunner securityContextRunner = Mock() {
    runAs(_, _) >> { args -> (args[1] as Runnable).run() }
  }
  NudgeDecisionService nudgeDecisionService = Mock()
  SavingsFundSuccessEmailResolver savingsFundSuccessEmailResolver = Mock()

  def paymentEmailSender = new PaymentEmailSender(paymentEmailService, securityContextRunner, nudgeDecisionService, savingsFundSuccessEmailResolver)

  def "send emails on payment creation"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    def decision = NudgeDecision.of(MEMBERSHIP)
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, ENGLISH)

    when:
    paymentEmailSender.onThirdPillarPaymentCreated(paymentCreatedEvent)

    then:
    1 * nudgeDecisionService.decide(user, THIRD_PILLAR_PAYMENT) >> decision
    1 * paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, decision, ENGLISH)
  }

  def "does not send emails on payment creation if member fee payment"() {
    given:
    def user = sampleUser().build()
    def payment = aNewSinglePayment()
    payment.paymentType = MEMBER_FEE
    def paymentCreatedEvent = new PaymentCreatedEvent(this, user, payment, ENGLISH)

    when:
    paymentEmailSender.onThirdPillarPaymentCreated(paymentCreatedEvent)

    then:
    0 * nudgeDecisionService._
    0 * paymentEmailService._
  }

  def "savings payment receipt decides the nudge for the paid account: #description"() {
    given:
    def user = sampleUser().build()
    def decision = NudgeDecision.of(ACCOUNT_RECURRING)
    def event = new SavingsPaymentCreatedEvent(this, user, ENGLISH, recipient)
    1 * savingsFundSuccessEmailResolver.resolve(event) >> email

    when:
    paymentEmailSender.onSavingsPaymentCreated(event)

    then:
    1 * nudgeDecisionService.decide(user, paidAccount, SAVINGS_FUND_PAYMENT) >> decision
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, email, decision, ENGLISH)

    where:
    description   | recipient                                        | paidAccount                                     | email
    "own account" | new PartyId(PartyId.Type.PERSON, "38812121215")  | new NudgeAccount(PERSON, "38812121215")         | SavingsFundPaymentEmail.personSuccess()
    "child"       | new PartyId(PartyId.Type.PERSON, "51111111111")  | new NudgeAccount(PERSON, "51111111111")         | SavingsFundPaymentEmail.childSuccess("Kid Tester", CHILD_LINK_ID)
    "company"     | new PartyId(PartyId.Type.LEGAL_ENTITY, "12345678") | new NudgeAccount(LEGAL_ENTITY, "12345678")    | SavingsFundPaymentEmail.companySuccess("Mesila OÜ", COMPANY_ID)
  }

  def "send email on savings payment cancel without a nudge"() {
    given:
    def user = sampleUser().build()
    def event = new SavingsPaymentCancelledEvent(this, user, ENGLISH)

    when:
    paymentEmailSender.onSavingsPaymentCancelled(event)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.cancelled(), ENGLISH)
    0 * nudgeDecisionService._
  }

  def "send email on savings payment failure without a nudge"() {
    given:
    def user = sampleUser().build()
    def event = new SavingsPaymentFailedEvent(this, user, ENGLISH)

    when:
    paymentEmailSender.onSavingsPaymentFailed(event)

    then:
    1 * paymentEmailService.sendSavingsFundPaymentEmail(user, SavingsFundPaymentEmail.failed(), ENGLISH)
    0 * nudgeDecisionService._
    0 * securityContextRunner._
  }

  def "send email on savings payment failure for a minor who cannot self authenticate"() {
    given:
    def minor = sampleUser().personalCode("51111111111").build()
    def event = new SavingsPaymentFailedEvent(this, minor, ENGLISH)
    securityContextRunner.runAs(_, _) >> { throw new MinorCannotSelfAuthenticateException(minor.personalCode) }

    when:
    paymentEmailSender.onSavingsPaymentFailed(event)

    then:
    noExceptionThrown()
    1 * paymentEmailService.sendSavingsFundPaymentEmail(minor, SavingsFundPaymentEmail.failed(), ENGLISH)
  }
}
