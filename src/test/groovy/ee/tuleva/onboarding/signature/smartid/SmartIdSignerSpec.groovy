package ee.tuleva.onboarding.signature.smartid

import ee.sk.smartid.*
import ee.sk.smartid.exception.permanent.SmartIdClientException
import ee.sk.smartid.rest.SmartIdConnector
import ee.sk.smartid.rest.dao.Interaction
import ee.sk.smartid.rest.dao.SessionStatus
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.signature.DigiDocFacade
import ee.tuleva.onboarding.signature.SignatureFile
import ee.tuleva.onboarding.signature.SmartIdSignatureSession
import org.digidoc4j.Container
import org.digidoc4j.DataToSign
import spock.lang.Specification

import java.security.cert.X509Certificate

import static ee.sk.smartid.HashType.SHA256

class SmartIdSignerSpec extends Specification {

  def smartIdClient = Mock(SmartIdClient)
  def smartIdConnector = Mock(SmartIdConnector)
  def sessionStore = Mock(GenericSessionStore)
  def digiDocFacade = Mock(DigiDocFacade)
  SmartIdSigner smartIdSigner

  def files = [new SignatureFile("test.txt", "text/plain", "Test".bytes)]
  def personalCode = "38888888888"
  def certificateSessionId = "certificateSessionId"
  def signingSessionId = "signatureSessionId"
  def documentNumber = "PNOEE-372123456"
  def certBuilder = Mock(CertificateRequestBuilder)
  def sigBuilder = Mock(SignatureRequestBuilder)
  def dataToSign = Mock(DataToSign)
  def container = Mock(Container)

  def setup() {
    smartIdSigner = new SmartIdSigner(smartIdClient, smartIdConnector, sessionStore, digiDocFacade)

    smartIdClient.getCertificate() >> certBuilder
    certBuilder./withSemanticsIdentifier|withCertificateLevel/(*_) >> certBuilder

    smartIdClient.createSignature() >> sigBuilder
    sigBuilder./withDocumentNumber|withSignableHash|withAllowedInteractionsOrder|withCertificateLevel/(*_) >> sigBuilder
  }

  def "start signing sets the certificateSessionId in the signature session"() {
    given:
    1 * certBuilder.initiateCertificateChoice() >> certificateSessionId

    when:
    def signatureSession = smartIdSigner.startSign(files, personalCode)

    then:
    signatureSession.personalCode == personalCode
    signatureSession.files == files
    signatureSession.certificateSessionId == certificateSessionId
  }

  def "start signing requests a QUALIFIED certificate for the given personal code"() {
    given:
    1 * certBuilder.initiateCertificateChoice() >> certificateSessionId

    when:
    smartIdSigner.startSign(files, personalCode)

    then:
    1 * certBuilder.withSemanticsIdentifier({ it.identifier == "PNOEE-$personalCode" }) >> certBuilder
    1 * certBuilder.withCertificateLevel("QUALIFIED") >> certBuilder
  }

  def "returns null while certificate session status is not yet available"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> null

    when:
    def file = smartIdSigner.getSignedFile(signatureSession)

    then:
    file == null
  }

  def "does nothing while certificate session is still RUNNING"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "RUNNING")

    when:
    def file = smartIdSigner.getSignedFile(signatureSession)

    then:
    file == null
  }

  def "throws when the session status is neither RUNNING nor COMPLETE"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "USER_REFUSED")

    when:
    smartIdSigner.getSignedFile(signatureSession)

    then:
    thrown(SmartIdClientException)
  }

  def "sets proper signature session variables when cert session is COMPLETE"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    def certSessionStatus = new SessionStatus(state: "COMPLETE")
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> certSessionStatus
    1 * certBuilder.createSmartIdCertificate(certSessionStatus) >> new SmartIdCertificate(
        certificate: Mock(X509Certificate),
        documentNumber: "docNr"
    )
    1 * digiDocFacade.dataToSign(*_) >> dataToSign
    1 * digiDocFacade.buildContainer(files) >> container
    1 * digiDocFacade.digestToSign(_) >> "digest".bytes
    1 * sigBuilder.initiateSigning() >> "signingSessionId"
    1 * sessionStore.save(signatureSession)

    when:
    def file = smartIdSigner.getSignedFile(signatureSession)

    then:
    file == null
    signatureSession.signingSessionId == "signingSessionId"
    signatureSession.verificationCode == "2084"
    signatureSession.documentNumber == "docNr"
    signatureSession.dataToSign == dataToSign
    signatureSession.signableHash.hash == "digest".bytes
    signatureSession.signableHash.hashType == SHA256
    signatureSession.container == container
  }

  def "does nothing while certificate session is COMPLETE and signing session is still RUNNING"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    signatureSession.signingSessionId = signingSessionId
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "COMPLETE")
    1 * smartIdConnector.getSessionStatus(signingSessionId) >> new SessionStatus(state: "RUNNING")

    when:
    def file = smartIdSigner.getSignedFile(signatureSession)

    then:
    file == null
  }

  def "throws when the signing session status is neither RUNNING nor COMPLETE"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    signatureSession.signingSessionId = signingSessionId
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> new SessionStatus(state: "COMPLETE")
    1 * smartIdConnector.getSessionStatus(signingSessionId) >> new SessionStatus(state: "TIMEOUT")

    when:
    smartIdSigner.getSignedFile(signatureSession)

    then:
    thrown(SmartIdClientException)
  }

  def "finalizes the signed file when signing session is complete"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    signatureSession.signingSessionId = signingSessionId
    signatureSession.dataToSign = dataToSign
    signatureSession.container = container
    signatureSession.documentNumber = documentNumber
    signatureSession.signableHash = new SignableHash()
    def completeSession = new SessionStatus(state: "COMPLETE")
    def signature = new SmartIdSignature(
        valueInBase64: "IA==",
        algorithmName: "SHA256",
        documentNumber: documentNumber
    )
    1 * smartIdConnector.getSessionStatus(certificateSessionId) >> completeSession
    1 * smartIdConnector.getSessionStatus(signingSessionId) >> completeSession
    1 * sigBuilder.createSmartIdSignature(*_) >> signature
    1 * digiDocFacade.addSignatureToContainer(signature.value, dataToSign, container) >> "lol".bytes

    when:
    def file = smartIdSigner.getSignedFile(signatureSession)

    then:
    new String(file) == "lol"
  }

  def "signature request asks for the QUALIFIED level with the display-and-PIN interaction"() {
    given:
    def signatureSession = new SmartIdSignatureSession(certificateSessionId, personalCode, files)
    signatureSession.signingSessionId = signingSessionId
    signatureSession.dataToSign = dataToSign
    signatureSession.container = container
    signatureSession.documentNumber = documentNumber
    signatureSession.signableHash = new SignableHash()
    def completeSession = new SessionStatus(state: "COMPLETE")
    def signature = new SmartIdSignature(valueInBase64: "IA==", algorithmName: "SHA256", documentNumber: documentNumber)
    smartIdConnector.getSessionStatus(certificateSessionId) >> completeSession
    smartIdConnector.getSessionStatus(signingSessionId) >> completeSession
    digiDocFacade.addSignatureToContainer(*_) >> "lol".bytes

    when:
    smartIdSigner.getSignedFile(signatureSession)

    then:
    1 * sigBuilder.withDocumentNumber(documentNumber) >> sigBuilder
    1 * sigBuilder.withSignableHash(signatureSession.signableHash) >> sigBuilder
    1 * sigBuilder.withAllowedInteractionsOrder({
      it.size() == 1 && it[0].displayText60 == "Tuleva: Sign Document"
    }) >> sigBuilder
    1 * sigBuilder.withCertificateLevel("QUALIFIED") >> sigBuilder
    1 * sigBuilder.createSmartIdSignature(completeSession) >> signature
  }
}
