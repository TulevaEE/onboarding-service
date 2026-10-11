package ee.tuleva.onboarding.member.email


import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.notification.email.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.nudge.NudgeDecisionService

import static ee.tuleva.onboarding.nudge.NudgeContext.MEMBERSHIP
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER
import ee.tuleva.onboarding.notification.email.EmailType
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceIntSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  NudgeDecisionService nudgeDecisionService = Mock() {
    decide(_, MEMBERSHIP) >> NudgeDecision.secondPillarTransfer(null)
  }
  MemberEmailService memberEmailService = new MemberEmailService(emailService, emailPersistenceService, nudgeDecisionService)

  def "SendMemberNumber"() {
    given:
    User user = sampleUser().build()
    def message = new MandrillMessage()
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    memberEmailService.sendMemberNumber(user, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_en", _, ["memberNumber", "nudge_second_pillar"]) >> message
    1 * emailService.send(user, message, "membership_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status, "nudge_second_pillar")

    when:
    memberEmailService.sendMemberNumber(user, Locale.of("et"))

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_et", _, ["memberNumber", "nudge_second_pillar"]) >> message
    1 * emailService.send(user, message, "membership_et") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status, "nudge_second_pillar")
  }
}
