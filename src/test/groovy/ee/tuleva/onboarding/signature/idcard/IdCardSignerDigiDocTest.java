package ee.tuleva.onboarding.signature.idcard;

import static java.util.Base64.getEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.digidoc4j.Configuration.Mode.TEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture;
import ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.CertificateWithKey;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import eu.europa.esig.dss.alert.exception.AlertException;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.List;
import org.digidoc4j.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdCardSignerDigiDocTest {

  private static final String PERSONAL_CODE = "38888888888";

  private final DigiDocFacade digiDocFacade = spy(new DigiDocFacade(new Configuration(TEST)));
  private final IdCardSigner idCardSigner = new IdCardSigner(digiDocFacade);
  private final SignableEntity entity = new SignableEntity("Mandate", 1L);
  private final byte[] signedContainer = "signed container".getBytes();

  @Test
  void finalizesAnRsaSignatureOverTheDataToSign() throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "RSA", 2048);
    var session = startSign(signer);
    var signatureValue = sign(signer.privateKey(), "SHA256withRSA", session);
    willReturn(signedContainer).given(digiDocFacade).addSignatureToContainer(any(), any(), any());

    var signedFile = idCardSigner.getSignedFile(session, entity, base64(signatureValue));

    assertThat(signedFile).isEqualTo(signedContainer);
    then(digiDocFacade)
        .should()
        .addSignatureToContainer(signatureValue, session.getDataToSign(), session.getContainer());
  }

  @ParameterizedTest
  @ValueSource(ints = {256, 384})
  void finalizesAnEcdsaSignatureInTheRawFormatWebEidReturns(int curveBits) throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", curveBits);
    var session = startSign(signer);
    var signatureValue = sign(signer.privateKey(), "SHA256withECDSAinP1363Format", session);
    willReturn(signedContainer).given(digiDocFacade).addSignatureToContainer(any(), any(), any());

    var signedFile = idCardSigner.getSignedFile(session, entity, base64(signatureValue));

    assertThat(signedFile).isEqualTo(signedContainer);
  }

  @ParameterizedTest
  @ValueSource(ints = {256, 384})
  void finalizesARawEcdsaSignatureWhoseLeadingBytesHappenToParseAsAsn1(int curveBits)
      throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", curveBits);
    var session = startSign(signer);
    var signatureValue = rawSignatureWhoseLeadingBytesParseAsAsn1(signer.privateKey(), session);
    willReturn(signedContainer).given(digiDocFacade).addSignatureToContainer(any(), any(), any());

    var signedFile = idCardSigner.getSignedFile(session, entity, base64(signatureValue));

    assertThat(signedFile).isEqualTo(signedContainer);
  }

  @ParameterizedTest
  @ValueSource(ints = {256, 384})
  void finalizesAnEcdsaSignatureInDerFormat(int curveBits) throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", curveBits);
    var session = startSign(signer);
    var signatureValue = sign(signer.privateKey(), "SHA256withECDSA", session);
    willReturn(signedContainer).given(digiDocFacade).addSignatureToContainer(any(), any(), any());

    var signedFile = idCardSigner.getSignedFile(session, entity, base64(signatureValue));

    assertThat(signedFile).isEqualTo(signedContainer);
  }

  @Test
  void rejectsADerEcdsaSignatureWithTrailingBytesBeforeFinalizing() throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", 384);
    var session = startSign(signer);
    var derSignature = sign(signer.privateKey(), "SHA256withECDSA", session);
    var derSignatureWithTrailingBytes = Arrays.copyOf(derSignature, derSignature.length + 1);

    assertThatThrownBy(
            () ->
                idCardSigner.getSignedFile(session, entity, base64(derSignatureWithTrailingBytes)))
        .isInstanceOf(InvalidSignatureException.class);
    then(digiDocFacade).should(never()).addSignatureToContainer(any(), any(), any());
  }

  @Test
  void rejectsAWellFormedRsaSignatureThatDoesNotVerifyBeforeFinalizing() throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "RSA", 2048);
    var session = startSign(signer);
    var signatureOverSomethingElse = base64(new byte[256]);

    assertThatThrownBy(
            () -> idCardSigner.getSignedFile(session, entity, signatureOverSomethingElse))
        .isInstanceOf(InvalidSignatureException.class);
    then(digiDocFacade).should(never()).addSignatureToContainer(any(), any(), any());
  }

  @Test
  void rejectsAnEcdsaSignatureMadeWithAnotherKeyThanTheSigningCertificatesBeforeFinalizing()
      throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", 384);
    var someoneElse = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", 384);
    var session = startSign(signer);
    var signatureByAnotherKey =
        sign(someoneElse.privateKey(), "SHA256withECDSAinP1363Format", session);

    assertThatThrownBy(
            () -> idCardSigner.getSignedFile(session, entity, base64(signatureByAnotherKey)))
        .isInstanceOf(InvalidSignatureException.class);
    then(digiDocFacade).should(never()).addSignatureToContainer(any(), any(), any());
  }

  @Test
  void rejectsAnEcdsaSignatureThatIsNeitherRawNorDerBeforeFinalizing() throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "EC", 384);
    var session = startSign(signer);

    assertThatThrownBy(() -> idCardSigner.getSignedFile(session, entity, base64(new byte[7])))
        .isInstanceOf(InvalidSignatureException.class);
    then(digiDocFacade).should(never()).addSignatureToContainer(any(), any(), any());
  }

  @Test
  void leavesAnAlertWhileTimestampingAVerifiedSignatureAsATechnicalError() throws Exception {
    var signer = WebEidCertificateFixture.signingCertificate(PERSONAL_CODE, "RSA", 2048);
    var session = startSign(signer);
    var signatureValue = sign(signer.privateKey(), "SHA256withRSA", session);
    var timestampIntegrityAlert = new AlertException("Timestamp integrity check failed");
    willThrow(timestampIntegrityAlert)
        .given(digiDocFacade)
        .addSignatureToContainer(any(), any(), any());

    assertThatThrownBy(() -> idCardSigner.getSignedFile(session, entity, base64(signatureValue)))
        .isSameAs(timestampIntegrityAlert);
  }

  private IdCardSignatureSession startSign(CertificateWithKey signer)
      throws CertificateEncodingException {
    return idCardSigner.startSign(
        entity,
        List.of(new SignatureFile("file.txt", "text/plain", "content".getBytes())),
        base64(signer.certificate().getEncoded()),
        List.of("SHA-256"),
        PERSONAL_CODE);
  }

  private static byte[] sign(
      PrivateKey privateKey, String algorithm, IdCardSignatureSession session)
      throws GeneralSecurityException {
    var signature = Signature.getInstance(algorithm);
    signature.initSign(privateKey);
    signature.update(session.getDataToSign().getDataToSign());
    return signature.sign();
  }

  private static byte[] rawSignatureWhoseLeadingBytesParseAsAsn1(
      PrivateKey privateKey, IdCardSignatureSession session) throws GeneralSecurityException {
    for (int attempt = 0; attempt < 1000; attempt++) {
      var signatureValue = sign(privateKey, "SHA256withECDSAinP1363Format", session);
      if (DSSASN1Utils.isAsn1Encoded(signatureValue)) {
        return signatureValue;
      }
    }
    throw new IllegalStateException("No raw ECDSA signature parsed as ASN.1 in 1000 attempts");
  }

  private static String base64(byte[] bytes) {
    return getEncoder().encodeToString(bytes);
  }
}
