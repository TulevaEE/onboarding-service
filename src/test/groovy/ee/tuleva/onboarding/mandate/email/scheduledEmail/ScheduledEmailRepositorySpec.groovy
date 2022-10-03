package ee.tuleva.onboarding.mandate.email.scheduledEmail


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

@DataJpaTest
class ScheduledEmailRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private ScheduledEmailRepository scheduledEmailRepository

  def "persisting and finding works"() {
    given:
    def user = entityManager.persist(sampleUserNonMember().id(null).build())
    def emailType = ScheduledEmailType.REMIND_THIRD_PILLAR_PAYMENT
    def scheduledEmail = entityManager.persist(
        new ScheduledEmail(user.id, "mandrillMessageId123", emailType)
    )
    entityManager.flush()

    when:
    def scheduledEmails =
        scheduledEmailRepository.findAllByUserIdAndTypeOrderByCreatedDateDesc(user.id, emailType)

    then:
    scheduledEmails == [scheduledEmail]
  }

}
