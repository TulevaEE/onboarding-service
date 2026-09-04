package ee.tuleva.onboarding.signature

import ee.tuleva.onboarding.auth.idcard.IdDocumentType
import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture
import org.digidoc4j.Configuration
import org.digidoc4j.Container
import org.digidoc4j.ContainerBuilder
import org.digidoc4j.DataToSign
import org.digidoc4j.DigestAlgorithm
import org.digidoc4j.Signature
import org.digidoc4j.SignatureProfile
import spock.lang.Specification

import java.security.MessageDigest

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER
import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.HTML
import static org.digidoc4j.Configuration.Mode.TEST

class DigiDocFacadeSpec extends Specification {

  def digiDocConfig = new Configuration(TEST)
  def digiDocFacade = new DigiDocFacade(digiDocConfig)

  def "builds a fresh container from one or more plain files"() {
    given:
    def files = [
        new SignatureFile("first.txt", HTML.mimeType, "hello".bytes),
        new SignatureFile("second.txt", HTML.mimeType, "world".bytes)
    ]

    when:
    def container = digiDocFacade.buildContainer(files)

    then:
    container.dataFiles.size() == 2
    container.dataFiles*.name.toSet() == ["first.txt", "second.txt"].toSet()
    container.dataFiles.find { it.name == "first.txt" }.bytes == "hello".bytes
    container.dataFiles.find { it.name == "second.txt" }.bytes == "world".bytes
  }

  def "builds a fresh container from a single plain file that is not itself a container"() {
    given:
    def files = [new SignatureFile("only.txt", HTML.mimeType, "content".bytes)]

    when:
    def container = digiDocFacade.buildContainer(files)

    then:
    container.dataFiles.size() == 1
    container.dataFiles.first().name == "only.txt"
    container.dataFiles.first().bytes == "content".bytes
  }

  def "reopens an existing digidoc container instead of wrapping it as a data file"() {
    given:
    def innerContainer = ContainerBuilder.aContainer()
        .withConfiguration(digiDocConfig)
        .withDataFile(new ByteArrayInputStream("inner content".bytes), "inner.txt", HTML.mimeType)
        .build()
    def out = new ByteArrayOutputStream()
    innerContainer.save(out)
    def existingContainerFile = new SignatureFile("container.asice", DIGIDOC_CONTAINER.mimeType, out.toByteArray())

    when:
    def container = digiDocFacade.buildContainer([existingContainerFile])

    then:
    container.dataFiles.size() == 1
    container.dataFiles.first().name == "inner.txt"
    container.dataFiles.first().bytes == "inner content".bytes
  }

  def "computes a signable digest from a real container and a real signing certificate"() {
    given:
    def container = ContainerBuilder.aContainer()
        .withConfiguration(digiDocConfig)
        .withDataFile(new ByteArrayInputStream("content".bytes), "file.txt", HTML.mimeType)
        .build()
    def certificate = WebEidCertificateFixture.certificate(
        "TEST", "USER", "38888888888", IdDocumentType.ESTONIAN_CITIZEN_ID_CARD)

    when:
    def dataToSign = digiDocFacade.dataToSign(container, certificate, DigestAlgorithm.SHA256)

    then:
    dataToSign.digestAlgorithm == DigestAlgorithm.SHA256
    dataToSign.signatureParameters.signatureProfile == SignatureProfile.LT
    dataToSign.dataToSign.length > 0
  }

  def "hashes the bytes to be signed with the digest algorithm of the data to sign"() {
    given:
    def dataToSign = Mock(DataToSign)
    1 * dataToSign.dataToSign >> "some data".bytes
    dataToSign.digestAlgorithm >> DigestAlgorithm.SHA256

    when:
    def digest = digiDocFacade.digestToSign(dataToSign)

    then:
    digest == MessageDigest.getInstance("SHA-256").digest("some data".bytes)
  }

  def "names the hash function of the data to sign the way Web eID expects it"() {
    given:
    def dataToSign = Mock(DataToSign)
    dataToSign.digestAlgorithm >> DigestAlgorithm.SHA256

    expect:
    digiDocFacade.hashFunction(dataToSign) == "SHA-256"
  }

  def "adds the finalized signature to the container and returns its bytes"() {
    given:
    def signatureValue = "signature-value".bytes
    def signature = Mock(Signature)
    def dataToSign = Mock(DataToSign)
    def container = Mock(Container)
    def containerBytes = "container bytes".bytes

    1 * dataToSign.finalize(signatureValue) >> signature
    1 * container.addSignature(signature)
    1 * container.saveAsStream() >> new ByteArrayInputStream(containerBytes)

    when:
    def result = digiDocFacade.addSignatureToContainer(signatureValue, dataToSign, container)

    then:
    result == containerBytes
  }
}
