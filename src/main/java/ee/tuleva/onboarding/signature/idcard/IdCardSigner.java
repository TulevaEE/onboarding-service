package ee.tuleva.onboarding.signature.idcard;

import static java.util.Base64.getDecoder;
import static java.util.Base64.getEncoder;

import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.SignatureFile;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdCardSigner {

  private final DigiDocFacade digiDocFacade;

  public IdCardSignatureSession startSign(List<SignatureFile> files, String certificate) {
    X509Certificate signingCertificate = decodeCertificate(certificate);
    Container container = digiDocFacade.buildContainer(files);
    DataToSign dataToSign = digiDocFacade.dataToSign(container, signingCertificate);
    String hashToSign = getEncoder().encodeToString(digiDocFacade.digestToSign(dataToSign));

    return new IdCardSignatureSession(
        hashToSign, digiDocFacade.hashFunction(dataToSign), dataToSign, container);
  }

  public byte[] getSignedFile(IdCardSignatureSession session, String signature) {
    return digiDocFacade.addSignatureToContainer(
        decodeSignature(signature), session.getDataToSign(), session.getContainer());
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
