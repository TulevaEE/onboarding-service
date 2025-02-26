package ee.tuleva.onboarding.mandate.email.persistence

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.emptyMandate
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch
import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SCHEDULED
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE

class EmailSpec extends Specification {

  def "toString works"() {
    given:
    def emailType = THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
    def sampleUser = sampleUser().build()
    def sampleMandate = emptyMandate().user(sampleUser).build()
    def mandateBatch = aSavedMandateBatch([sampleMandate])
    sampleMandate.mandateBatch = mandateBatch
    def email = new Email(personalCode: sampleUser.personalCode, mandrillMessageId: "123", type: emailType, mandateBatch: mandateBatch, status: SCHEDULED)

    when:
    println email.toString()

    then:
    noExceptionThrown()
  }

}
