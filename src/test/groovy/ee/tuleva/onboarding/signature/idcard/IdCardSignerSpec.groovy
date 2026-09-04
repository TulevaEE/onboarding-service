package ee.tuleva.onboarding.signature.idcard

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture
import ee.tuleva.onboarding.signature.DigiDocFacade
import ee.tuleva.onboarding.signature.IdCardSignatureSession
import ee.tuleva.onboarding.signature.SignatureFile
import org.digidoc4j.Container
import org.digidoc4j.DataToSign
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD
import static java.util.Base64.getDecoder
import static java.util.Base64.getEncoder

class IdCardSignerSpec extends Specification {

  DigiDocFacade digiDocFacade = Mock()
  IdCardSigner idCardSigner = new IdCardSigner(digiDocFacade)

  def files = [new SignatureFile("fileName", "mimeType", "content".bytes)]
  def certificate = WebEidCertificateFixture.certificate("TEST", "USER", "38888888888", ESTONIAN_CITIZEN_ID_CARD)
  def certificateInBase64 = getEncoder().encodeToString(certificate.encoded)

  def "starts an id card signature from a base64 DER certificate and returns the base64 hash to sign"() {
    given:
    def container = Mock(Container)
    def dataToSign = Mock(DataToSign)
    def digestToSign = "digest".bytes

    1 * digiDocFacade.buildContainer(files) >> container
    1 * digiDocFacade.dataToSign(container, certificate) >> dataToSign
    1 * digiDocFacade.digestToSign(dataToSign) >> digestToSign
    1 * digiDocFacade.hashFunction(dataToSign) >> "SHA-256"

    when:
    def session = idCardSigner.startSign(files, certificateInBase64)

    then:
    getDecoder().decode(session.hashToSign) == digestToSign
    session.hashFunction == "SHA-256"
    session.container == container
    session.dataToSign == dataToSign
  }

  def "rejects a signing certificate that is not a base64 DER certificate"() {
    when:
    idCardSigner.startSign(files, invalidCertificate)

    then:
    thrown(InvalidSigningCertificateException)

    where:
    invalidCertificate << ["not base64!", getEncoder().encodeToString("not a certificate".bytes)]
  }

  def "rejects a signature that is not base64"() {
    given:
    def session = new IdCardSignatureSession("aGFzaA==", "SHA-256", Mock(DataToSign), Mock(Container))

    when:
    idCardSigner.getSignedFile(session, "not base64!")

    then:
    thrown(InvalidSignatureException)
    0 * digiDocFacade.addSignatureToContainer(_, _, _)
  }

  def "adds the base64 signature to the container of the session"() {
    given:
    def dataToSign = Mock(DataToSign)
    def container = Mock(Container)
    def session = new IdCardSignatureSession("aGFzaA==", "SHA-256", dataToSign, container)
    def signature = "signature".bytes
    def signedContainer = "signed".bytes

    1 * digiDocFacade.addSignatureToContainer(signature, dataToSign, container) >> signedContainer

    when:
    def signedFile = idCardSigner.getSignedFile(session, getEncoder().encodeToString(signature))

    then:
    signedFile == signedContainer
  }
}
