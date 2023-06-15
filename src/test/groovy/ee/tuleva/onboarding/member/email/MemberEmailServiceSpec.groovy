package ee.tuleva.onboarding.member.email


import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceSpec extends Specification {

  EmailService emailService = Mock(EmailService)
  MemberEmailService memberService = new MemberEmailService(emailService)

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

    when:
    memberService.sendMemberNumber(user, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "member_en", mergeVars, tags, null) >> message
    1 * emailService.send(user, message, "member_en")
  }

}
