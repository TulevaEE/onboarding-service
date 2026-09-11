package ee.tuleva.onboarding.member.email


import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.notification.email.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.notification.email.EmailType
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberEmailServiceIntSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  MemberEmailService memberEmailService = new MemberEmailService(emailService, emailPersistenceService)

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
    1 * emailService.newMandrillMessage(user.email, "membership_en", _, ["memberNumber"]) >> message
    1 * emailService.send(user, message, "membership_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status)

    when:
    memberEmailService.sendMemberNumber(user, Locale.of("et"))

    then:
    1 * emailService.newMandrillMessage(user.email, "membership_et", _, ["memberNumber"]) >> message
    1 * emailService.send(user, message, "membership_et") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, EmailType.MEMBERSHIP, mandrillResponse.status)
  }
}
