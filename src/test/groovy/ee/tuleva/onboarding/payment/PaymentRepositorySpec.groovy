package ee.tuleva.onboarding.payment


import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment

@DataJpaTest
class PaymentRepositorySpec extends Specification {
  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private PaymentRepository paymentRepository

  def "persisting and findById() works"() {
    given:
    User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    Payment paymentToBeSaved = aNewSinglePayment()
    paymentToBeSaved.id = null
    paymentToBeSaved.user = sampleUser

    Payment savedPayment = entityManager.persist(paymentToBeSaved)

    entityManager.flush()

    when:
    def foundPayment = paymentRepository.findById(savedPayment.id)

    then:
    foundPayment.isPresent()
    with(foundPayment.get()) {
      id != null
      user == sampleUser
      amount == paymentToBeSaved.amount
      internalReference == paymentToBeSaved.internalReference
      createdTime != null
    }
  }

  def "findByInternalReference"() {
    given:
    User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    Payment paymentToBeSaved = aNewSinglePayment()
    paymentToBeSaved.id = null
    paymentToBeSaved.user = sampleUser

    Payment savedPayment = entityManager.persist(paymentToBeSaved)

    entityManager.flush()

    when:
    def foundPayment =
        paymentRepository.findByInternalReference(savedPayment.getInternalReference())

    then:
    foundPayment.isPresent()
    with(foundPayment.get()) {
      id != null
      user == sampleUser
      amount == paymentToBeSaved.amount
      internalReference == paymentToBeSaved.internalReference
      createdTime != null
    }
  }

  def "can find by personal code"() {
    given:
    User sampleUser = entityManager.persist(sampleUserNonMember().id(null).build())
    Payment payment = aNewSinglePayment().tap {
      id = null
      user = sampleUser
      recipientPersonalCode = sampleUser.personalCode
    }
    entityManager.persist(payment)
    entityManager.flush()

    when:
    def payments = paymentRepository.findAllByRecipientPersonalCodeAndPaymentTypeNot(sampleUser.personalCode, MEMBER_FEE)

    then:
    payments.size() == 1
    with(payments[0]) {
      id != null
      user == sampleUser
      amount == payment.amount
      internalReference == payment.internalReference
      createdTime != null
    }
  }
}
