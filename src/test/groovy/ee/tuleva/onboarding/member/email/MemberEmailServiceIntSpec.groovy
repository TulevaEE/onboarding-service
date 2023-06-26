package ee.tuleva.onboarding.member.email


import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Ignore
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

@SpringBootTest
@Ignore
class MemberEmailServiceIntSpec extends Specification {

  @Autowired
  MemberEmailService memberEmailService

  def "SendMemberNumber"() {
    given:
    User user = sampleUser().email("erko@risthein.ee").build()

    when:
    memberEmailService.sendMemberNumber(user, Locale.ENGLISH)
    memberEmailService.sendMemberNumber(user, new Locale("et"))

    then:
    true
  }
}
