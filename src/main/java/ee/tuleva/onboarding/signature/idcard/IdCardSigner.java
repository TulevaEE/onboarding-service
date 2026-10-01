package ee.tuleva.onboarding.signature.idcard;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;

import ee.tuleva.onboarding.personalcode.PersonalCode;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.SignableEntity;
import ee.tuleva.onboarding.signature.SignatureFile;
import eu.europa.esig.dss.alert.exception.AlertException;
import eu.webeid.security.certificate.CertificateData;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.utils.TokenAlgorithmSupport;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdCardSigner {

  private final DigiDocFacade digiDocFacade;

  public IdCardSignatureSession startSign(
      SignableEntity entity,
      List<SignatureFile> files,
      String certificate,
      List<String> supportedHashFunctions,
      String personalCode) {
    X509Certificate signingCertificate = decodeCertificate(certificate);
    requireBelongsToSigner(signingCertificate, personalCode);
    DigestAlgorithm digestAlgorithm =
        requireSupported(
            TokenAlgorithmSupport.determineSignatureDigestAlgorithm(signingCertificate),
            supportedHashFunctions);

    Container container = digiDocFacade.buildContainer(files);
    DataToSign dataToSign =
        digiDocFacade.dataToSign(container, signingCertificate, digestAlgorithm);
    String hashToSign = getEncoder().encodeToString(digiDocFacade.digestToSign(dataToSign));

    return new IdCardSignatureSession(
        entity, hashToSign, digiDocFacade.hashFunction(dataToSign), dataToSign, container);
  }

  public byte[] getSignedFile(
      IdCardSignatureSession session, SignableEntity entity, String signature) {
    session.requireStartedFor(entity);
    byte[] signatureValue = decodeSignature(signature);
    try {
      return digiDocFacade.addSignatureToContainer(
          signatureValue, session.getDataToSign(), session.getContainer());
    } catch (AlertException signatureOrSigningCertificateRejected) {
      throw new InvalidSignatureException(signatureOrSigningCertificateRejected);
    }
  }

  private static void requireBelongsToSigner(X509Certificate certificate, String personalCode) {
    if (!subjectIdCode(certificate).equals(personalCode)) {
      throw new SigningCertificateMismatchException();
    }
  }

  private static String subjectIdCode(X509Certificate certificate) {
    try {
      return PersonalCode.fromSubjectIdCode(
          CertificateData.getSubjectIdCode(certificate)
              .orElseThrow(SigningCertificateMismatchException::new));
    } catch (CertificateEncodingException e) {
      throw new InvalidSigningCertificateException(e);
    }
  }

  private static DigestAlgorithm requireSupported(
      DigestAlgorithm digestAlgorithm, List<String> supportedHashFunctions) {
    String hashFunction = digestAlgorithm.getDssDigestAlgorithm().getJavaName();
    if (!supportedHashFunctions.contains(hashFunction)) {
      throw new UnsupportedHashFunctionException(hashFunction, supportedHashFunctions);
    }
    return digestAlgorithm;
  }

  private static byte[] decodeSignature(String signature) {
    try {
      return getDecoder().decode(signature);
    } catch (IllegalArgumentException e) {
      throw new InvalidSignatureException(e);
    }
  }

  private static X509Certificate decodeCertificate(String certificate) {
    try {
      return (X509Certificate)
          CertificateFactory.getInstance("X.509")
              .generateCertificate(new ByteArrayInputStream(getDecoder().decode(certificate)));
    } catch (CertificateException | IllegalArgumentException e) {
      throw new InvalidSigningCertificateException(e);
    }
  }
}
