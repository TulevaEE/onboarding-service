package ee.tuleva.onboarding.mandate.email

import ee.tuleva.onboarding.mandate.batch.MandateBatch
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleFundPensionOpeningMandate
import static ee.tuleva.onboarding.mandate.MandateFixture.samplePartialWithdrawalMandate
import static ee.tuleva.onboarding.mandate.batch.MandateBatchFixture.aSavedMandateBatch

class MandateBatchEmailSenderSpec extends Specification {

  MandateBatchEmailService mandateBatchEmailService = Mock()
  MandateBatchEmailSender sender = new MandateBatchEmailSender(mandateBatchEmailService)

  def "sends the batch email"() {
    given:
    User user = sampleUser().build()
    MandateBatch batch = aSavedMandateBatch([samplePartialWithdrawalMandate(), sampleFundPensionOpeningMandate()])

    when:
    sender.sendBatchEmail(new AfterMandateBatchSignedEvent(this, user, batch, Locale.ENGLISH))

    then:
    1 * mandateBatchEmailService.sendMandateBatch(user, batch, Locale.ENGLISH)
  }

  def "sends the batch failed email"() {
    given:
    User user = sampleUser().build()
    MandateBatch batch = aSavedMandateBatch([samplePartialWithdrawalMandate()])

    when:
    sender.sendBatchFailedEmail(new OnMandateBatchFailedEvent(this, user, batch, Locale.ENGLISH))

    then:
    1 * mandateBatchEmailService.sendMandateBatchFailedEmail(user, batch, Locale.ENGLISH)
  }
}
