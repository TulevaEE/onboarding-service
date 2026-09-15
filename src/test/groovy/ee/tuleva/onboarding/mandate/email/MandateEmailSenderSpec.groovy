package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.batch.MandateBatch
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.nudge.NudgeDecisionService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandateWithPaymentRate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch
import static ee.tuleva.onboarding.nudge.NudgeContext.SECOND_PILLAR_MANDATE
import static ee.tuleva.onboarding.nudge.NudgeContext.SECOND_PILLAR_PAYMENT_RATE
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_MANDATE
import static ee.tuleva.onboarding.nudge.NudgeKey.MEMBERSHIP

class MandateEmailSenderSpec extends Specification {

  MandateEmailService mandateEmailService = Mock()
  NudgeDecisionService nudgeDecisionService = Mock()
  MandateEmailSender mandateEmailSender = new MandateEmailSender(mandateEmailService, nudgeDecisionService)

  def "decides the nudge for the flow the mandate belongs to and sends the email: #description"() {
    given:
    User user = sampleUser().build()
    def decision = NudgeDecision.of(MEMBERSHIP)
    def event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    when:
    mandateEmailSender.sendEmail(event)

    then:
    1 * nudgeDecisionService.decide(user, context) >> decision
    1 * mandateEmailService.sendMandate(user, mandate, decision, Locale.ENGLISH)

    where:
    description              | mandate                       || context
    "second pillar mandate"  | sampleMandate()               || SECOND_PILLAR_MANDATE
    "payment rate change"    | sampleMandateWithPaymentRate() || SECOND_PILLAR_PAYMENT_RATE
    "third pillar mandate"   | thirdPillarMandate()          || THIRD_PILLAR_MANDATE
  }

  def "does not send a separate email for a mandate that is part of a batch"() {
    given:
    User user = sampleUser().build()
    Mandate mandate = samplePartialWithdrawalMandate()
    MandateBatch batch = aSavedMandateBatch([mandate, sampleFundPensionOpeningMandate()])
    mandate.setMandateBatch(batch)
    def event = new AfterMandateSignedEvent(this, user, mandate, Locale.ENGLISH)

    when:
    mandateEmailSender.sendEmail(event)

    then:
    0 * nudgeDecisionService._
    0 * mandateEmailService._
  }
}
