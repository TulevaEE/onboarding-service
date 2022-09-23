package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewPayment
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

@DataJpaTest
class PaymentRepositorySpec extends Specification {
  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private PaymentRepository paymentRepository

  def "persisting and findById() works"() {
    given:
    User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    Payment paymentToBeSaved = aNewPayment
    paymentToBeSaved.id = null
    paymentToBeSaved.user = sampleUser

    Payment savedPayment = entityManager.persist(paymentToBeSaved)

    entityManager.flush()

    when:
    def foundPayment = paymentRepository.findById(savedPayment.id)

    then:
    foundPayment.isPresent()
    foundPayment.get().id != null
    foundPayment.get().user == sampleUser
    foundPayment.get().getAmount() == aNewPayment.amount
    foundPayment.get().internalReference == aNewPayment.internalReference
    foundPayment.get().createdTime != null
  }

  def "findByInternalReference"() {
    given:
    User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    Payment paymentToBeSaved = aNewPayment
    paymentToBeSaved.id = null
    paymentToBeSaved.user = sampleUser

    Payment savedPayment = entityManager.persist(paymentToBeSaved)

    entityManager.flush()

    when:
    def foundPayment =
        paymentRepository.findByInternalReference(savedPayment.getInternalReference())

    then:
    foundPayment.isPresent()
    foundPayment.get().id != null
    foundPayment.get().user == sampleUser
    foundPayment.get().getAmount() == aNewPayment.amount
    foundPayment.get().internalReference == aNewPayment.internalReference
    foundPayment.get().createdTime != null

  }

}
