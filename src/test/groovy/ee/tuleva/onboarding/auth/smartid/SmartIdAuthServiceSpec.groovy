package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.AuthenticationHash
import ee.sk.smartid.AuthenticationIdentity
import ee.sk.smartid.AuthenticationResponseValidator
import ee.sk.smartid.SmartIdClient
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException
import ee.sk.smartid.exception.useraction.UserRefusedException
import ee.sk.smartid.rest.SmartIdConnector
import ee.sk.smartid.rest.dao.*
import spock.lang.Specification

import java.sql.Time
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.*

class SmartIdAuthServiceSpec extends Specification {

  SmartIdAuthService smartIdAuthService
  SmartIdAuthenticationHashGenerator hashGenerator = Mock(SmartIdAuthenticationHashGenerator)
  AuthenticationResponseValidator validator = Mock(AuthenticationResponseValidator)
  SmartIdConnector connector = Mock(SmartIdConnector)
  AuthenticationHash hash

  def setup() {
    SmartIdClient smartIdClient = new SmartIdClient()
    smartIdClient.setSmartIdConnector(connector)
    smartIdClient.setRelyingPartyUUID("00000000-0000-0000-0000-000000000000")
    smartIdClient.setRelyingPartyName("Demo")

    hash = AuthenticationHash.generateRandomHash()
    hashGenerator.generateHash() >> hash

    smartIdAuthService = new SmartIdAuthService(smartIdClient, hashGenerator, validator)
  }

  def "StartLogin: Start smart id login generates hash"() {
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    then:
    session.verificationCode == hash.calculateVerificationCode()
    session.personalCode == personalCode
    session.authenticationHash == hash
  }

  def "IsLoginComplete: Login is not complete when result is not valid"() {
    given:
    1 * connector.authenticate(_ as SemanticsIdentifier, _) >>
        response(aSessionId)
    1 * connector.getSessionStatus(aSessionId) >> sessionStatus("COMPLETE", "DOCUMENT_UNUSABLE")
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    waitForLoginComplete(session)
    then:
    thrown(SmartIdException)
  }

  def "IsLoginComplete: Login is not complete when user account not found"() {
    given:
    1 * connector.authenticate(_ as SemanticsIdentifier, _) >>
        response(aSessionId)
    1 * connector.getSessionStatus(aSessionId) >> {
      throw new UserAccountNotFoundException()
    }
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    waitForLoginComplete(session)
    then:
    thrown(SmartIdException)
  }

  def "IsLoginComplete: Login is not complete when user refused authentication"() {
    given:
    1 * connector.authenticate(_ as SemanticsIdentifier, _) >>
        response(aSessionId)
    1 * connector.getSessionStatus(aSessionId) >> {
      throw new UserRefusedException()
    }
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    waitForLoginComplete(session)
    then:
    thrown(SmartIdException)
  }

  def "IsLoginComplete: Fetch state of smart id login"() {
    given:
    1 * connector.authenticate(_ as SemanticsIdentifier, _) >>
        response(aSessionId)
    1 * connector.getSessionStatus(aSessionId) >> sessionStatus("COMPLETE", "OK", "sessionSignature")
    1 * validator.validate(_) >> validAuthIdentity()
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    boolean isLoginComplete = waitForLoginComplete(session)
    then:
    isLoginComplete
  }

  def "IsLoginComplete: Error with authentication result"() {
    given:
    1 * connector.authenticate(_ as SemanticsIdentifier, _) >>
        response(aSessionId)
    1 * connector.getSessionStatus(aSessionId) >> sessionStatus("COMPLETE", "OK", "signature")
    1 * validator.validate(_) >> {
      throw new UnprocessableSmartIdResponseException("Something went wrong");
    }
    when:
    SmartIdSession session = smartIdAuthService.startLogin(personalCode)
    waitForLoginComplete(session)
    then:
    thrown(SmartIdException)
  }

  private boolean waitForLoginComplete(SmartIdSession session) {
    int pollCount = 0
    while (true) {
      if (smartIdAuthService.getAuthenticationIdentity(session.authenticationHash.hashInBase64).isPresent()) {
        return true
      }
      pollCount++
      if (pollCount > 10) {
        throw new TimeoutException('Polling timed out')
      }
      TimeUnit.MILLISECONDS.sleep(20)
    }
  }

  private static AuthenticationIdentity validAuthIdentity() {
    AuthenticationIdentity identity = new AuthenticationIdentity()
    identity.givenName = firstName
    identity.surname = lastName
    return identity
  }

  private static SessionStatus sessionStatus(String state, String endResult = null, String sessionSignature = null) {
    def result = new SessionResult()
    result.endResult = endResult
    result.documentNumber = "PNOEE-372123456"

    def signature = new SessionSignature()
    signature.algorithm = "sha256WithRSAEncryption"
    signature.value = sessionSignature

    def certificate = new SessionCertificate()
    certificate.certificateLevel = "QUALIFIED"
    certificate
        .value = "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"

    def status = new SessionStatus()
    status.setState(state)
    status.setResult(result)
    status.signature = signature
    status.cert = certificate

    return status
  }

  AuthenticationSessionResponse response(String sessionId) {
    def response = new AuthenticationSessionResponse()
    response.setSessionID(sessionId)
    return response
  }
}
