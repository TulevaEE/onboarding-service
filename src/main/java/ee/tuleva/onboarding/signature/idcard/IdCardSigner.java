package ee.tuleva.onboarding.signature.idcard;

import ee.tuleva.onboarding.auth.ocsp.OCSPUtils;
import ee.tuleva.onboarding.signature.DigiDocFacade;
import ee.tuleva.onboarding.signature.SignatureFile;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdCardSigner {

  private final OCSPUtils ocspUtils;
  private final DigiDocFacade digiDocFacade;

  public IdCardSignatureSession startSign(List<SignatureFile> files, String signingCertificate) {
    X509Certificate certificate = ocspUtils.decodeX09Certificate(signingCertificate);
    Container container = digiDocFacade.buildContainer(files);

    DataToSign dataToSign = digiDocFacade.dataToSign(container, certificate);
    byte[] digestToSign = digiDocFacade.digestToSign(dataToSign);
    String hashToSignInHex = Hex.encodeHexString(digestToSign);

    return new IdCardSignatureSession(hashToSignInHex, dataToSign, container);
  }

  @SneakyThrows
  public byte[] getSignedFile(IdCardSignatureSession session, String signedHashInHex) {
    byte[] signature = Hex.decodeHex(signedHashInHex);
    return digiDocFacade.addSignatureToContainer(
        signature, session.getDataToSign(), session.getContainer());
  }
}
