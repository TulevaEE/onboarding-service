package ee.tuleva.onboarding.mandate.email.webhook

import jakarta.servlet.http.HttpServletRequest
import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

class MandrillSignatureVerifierSpec extends Specification {

  MandrillSignatureVerifier verifier = new MandrillSignatureVerifier()
  String webhookKey = "test_webhook_key_123"
  String apiUrl = "https://onboarding-service.tuleva.ee"

  def "verify returns false when webhook key is not configured"() {
    given:
    verifier.webhookKey = null
    verifier.apiUrl = apiUrl
    def request = Mock(HttpServletRequest)

    when:
    def result = verifier.verify(request, "some_signature")

    then:
    !result
  }

  def "verify returns false when signature is null"() {
    given:
    verifier.webhookKey = webhookKey
    verifier.apiUrl = apiUrl
    def request = Mock(HttpServletRequest)

    when:
    def result = verifier.verify(request, null)

    then:
    !result
  }

  def "verify returns false when signature is empty"() {
    given:
    verifier.webhookKey = webhookKey
    verifier.apiUrl = apiUrl
    def request = Mock(HttpServletRequest)

    when:
    def result = verifier.verify(request, "")

    then:
    !result
  }

  def "verify returns true for valid signature"() {
    given:
    verifier.webhookKey = webhookKey
    verifier.apiUrl = apiUrl
    def url = apiUrl + "/v1/emails/webhooks/mandrill"
    def mandrillEvents = '[{"event":"open"}]'
    def expectedSignature = generateTestSignature(url, mandrillEvents)

    def request = Mock(HttpServletRequest) {
      getParameterMap() >> ["mandrill_events": [mandrillEvents] as String[]]
    }

    when:
    def result = verifier.verify(request, expectedSignature)

    then:
    result
  }

  def "verify returns false for invalid signature"() {
    given:
    verifier.webhookKey = webhookKey
    verifier.apiUrl = apiUrl
    def request = Mock(HttpServletRequest) {
      getParameterMap() >> ["mandrill_events": ['[{"event":"open"}]'] as String[]]
    }

    when:
    def result = verifier.verify(request, "invalid_signature_base64")

    then:
    !result
  }

  private String generateTestSignature(String url, String mandrillEvents) {
    String signedData = url + "mandrill_events" + mandrillEvents

    Mac mac = Mac.getInstance("HmacSHA1")
    SecretKeySpec secretKey = new SecretKeySpec(webhookKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1")
    mac.init(secretKey)

    byte[] hmac = mac.doFinal(signedData.getBytes(StandardCharsets.UTF_8))
    return Base64.getEncoder().encodeToString(hmac)
  }
}
