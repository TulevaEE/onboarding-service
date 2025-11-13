package ee.tuleva.onboarding.mandate.email.webhook

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

class MandrillSignatureVerifierSpec extends Specification {

  MandrillSignatureVerifier verifier = new MandrillSignatureVerifier()
  String webhookKey = "test_webhook_key_123"

  def "verify returns false when webhook key is not configured"() {
    given:
    verifier.webhookKey = null
    def request = new MockHttpServletRequest()

    when:
    def result = verifier.verify(request, "some_signature")

    then:
    !result
  }

  def "verify returns false when signature is null"() {
    given:
    verifier.webhookKey = webhookKey
    def request = new MockHttpServletRequest()

    when:
    def result = verifier.verify(request, null)

    then:
    !result
  }

  def "verify returns false when signature is empty"() {
    given:
    verifier.webhookKey = webhookKey
    def request = new MockHttpServletRequest()

    when:
    def result = verifier.verify(request, "")

    then:
    !result
  }

  def "verify returns true for valid signature"() {
    given:
    verifier.webhookKey = webhookKey
    String host = "onboarding-service.tuleva.ee"
    String webhookPath = "/v1/emails/webhooks/mandrill"
    String mandrillEvents = '[{"event":"open"}]'

    def request = new MockHttpServletRequest().tap {
      scheme = "https"
      serverName = host
      serverPort = 443
      requestURI = webhookPath
      addParameter("mandrill_events", mandrillEvents)
    }

    when:
    def result = verifier.verify(request, "au95xsZLFFgTtfD1Ipa2mX7CXuo=")

    then:
    result
  }

  def "verify returns false for invalid signature"() {
    given:
    verifier.webhookKey = webhookKey
    def request = new MockHttpServletRequest().tap {
      scheme = "https"
      serverName = "onboarding-service.tuleva.ee"
      serverPort = 443
      requestURI = "/v1/emails/webhooks/mandrill"
      addParameter("mandrill_events", '[{"event":"open"}]')
    }

    when:
    def result = verifier.verify(request, "invalid_signature_base64")

    then:
    !result
  }

  def "verify handles empty events array from Mandrill"() {
    given:
    verifier.webhookKey = webhookKey
    String host = "onboarding-service.tuleva.ee"
    String webhookPath = "/v1/emails/webhooks/mandrill"
    String emptyEventsArray = "[]"

    def request = new MockHttpServletRequest().tap {
      scheme = "https"
      serverName = host
      serverPort = 443
      requestURI = webhookPath
      addParameter("mandrill_events", emptyEventsArray)
    }

    when:
    def result = verifier.verify(request, "0gS1zQW7WR/S2+dKHVUmSz4D8Sc=")

    then:
    result
  }
}
