package ee.tuleva.onboarding.member.email


import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.mandate.email.persistence.EmailType
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  MemberEmailService memberService = new MemberEmailService(emailService, emailPersistenceService)

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
    ]
    def tags = ["memberNumber"]
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    memberService.sendMemberNumber(user, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_en", mergeVars, tags, null) >> message
    1 * emailService.send(user, message, "membership_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status)
  }

}
