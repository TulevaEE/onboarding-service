package ee.tuleva.onboarding.mandate

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getAttachments
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class EmailVariablesAttachmentsSpec extends Specification {

  def "getAttachments builds a single signed container attachment named after the user and mandate id"() {
    given:
    def user = sampleUser().build()
    def mandate = sampleMandate()

    when:
    def attachments = getAttachments(user, mandate)

    then:
    attachments.size() == 1
    attachments.first().name == "jordan_valdma_avaldus_123.asice"
    attachments.first().type == "application/vnd.etsi.asic-e+zip"
    attachments.first().content == Base64.encoder.encodeToString(mandate.getSignedFile())
  }
}
