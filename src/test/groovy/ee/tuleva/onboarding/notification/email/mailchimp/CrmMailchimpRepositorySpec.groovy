package ee.tuleva.onboarding.notification.email.mailchimp

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@JdbcTest
@Import(CrmMailchimpRepository)
@Transactional
class CrmMailchimpRepositorySpec extends Specification {

  @Autowired
  CrmMailchimpRepository repository

  @Autowired
  JdbcClient jdbcClient

  def "findPersonalCodeByEmail returns personal code when email exists"() {
    given:
    insertCrmRecord('38512310215', 'test@example.com', 'ET', 30)

    when:
    def result = repository.findPersonalCodeByEmail('test@example.com')

    then:
    result.isPresent()
    result.get() == '38512310215'
  }

  def "findPersonalCodeByEmail returns empty when email does not exist"() {
    when:
    def result = repository.findPersonalCodeByEmail('nonexistent@example.com')

    then:
    !result.isPresent()
  }

  def "findPersonalCodeByEmail returns empty when email is null in database"() {
    given:
    insertCrmRecord('38512310216', null, 'ET', 25)

    when:
    def result = repository.findPersonalCodeByEmail(null)

    then:
    !result.isPresent()
  }

  def "findPersonalCodeByEmail handles multiple records correctly"() {
    given:
    insertCrmRecord('38512310215', 'user1@example.com', 'ET', 30)
    insertCrmRecord('38512310216', 'user2@example.com', 'EN', 25)
    insertCrmRecord('38512310217', 'user3@example.com', 'ET', 35)

    when:
    def result1 = repository.findPersonalCodeByEmail('user1@example.com')
    def result2 = repository.findPersonalCodeByEmail('user2@example.com')
    def result3 = repository.findPersonalCodeByEmail('user3@example.com')

    then:
    result1.get() == '38512310215'
    result2.get() == '38512310216'
    result3.get() == '38512310217'
  }

  private void insertCrmRecord(String personalCode, String email, String language, int age) {
    jdbcClient.sql("""
      INSERT INTO analytics.mv_crm_mailchimp (isikukood, email, keel, vanus)
      VALUES (:personalCode, :email, :language, :age)
    """)
        .param("personalCode", personalCode)
        .param("email", email)
        .param("language", language)
        .param("age", age)
        .update()
  }
}
