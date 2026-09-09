package ee.tuleva.onboarding.member.email


import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.notification.email.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailType
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.nudge.NudgeDecision
import ee.tuleva.onboarding.nudge.NudgeDecisionService

import static ee.tuleva.onboarding.nudge.NudgeContext.MEMBERSHIP
import static ee.tuleva.onboarding.nudge.NudgeKey.SECOND_PILLAR_TRANSFER
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  NudgeDecisionService nudgeDecisionService = Mock() {
    decide(_, MEMBERSHIP) >> NudgeDecision.secondPillarTransfer(null)
  }
  MemberEmailService memberService = new MemberEmailService(emailService, emailPersistenceService, nudgeDecisionService)

  def "send member number email"() {
    given:
    def user = sampleUser().build()
    def locale = Locale.ENGLISH
    def message = new MandrillMessage()
    def mergeVars = [
        fname       : user.firstName,
        lname       : user.lastName,
        memberNumber: user.memberOrThrow.memberNumber,
        memberDate  : "31.01.2017"
    ] + NudgeDecision.secondPillarTransfer(null).mergeVars(locale)
    def tags = ["memberNumber", "nudge_second_pillar"]
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    memberService.sendMemberNumber(user, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_en", mergeVars, tags) >> message
    1 * emailService.send(user, message, "membership_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status, "nudge_second_pillar")
  }

  def "still sends the member number email when the nudge cannot be decided"() {
    given:
    def user = sampleUser().build()
    def message = new MandrillMessage()
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }
    NudgeDecisionService failing = Mock() {
      decide(_, MEMBERSHIP) >> { throw new IllegalStateException("EPIS down") }
    }
    def service = new MemberEmailService(emailService, emailPersistenceService, failing)

    when:
    service.sendMemberNumber(user, Locale.ENGLISH)

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_en", _, ["memberNumber", "nudge_none"]) >> message
    1 * emailService.send(user, message, "membership_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status, "nudge_none")
  }
}
