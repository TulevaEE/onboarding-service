package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.SignatureFile;
import ee.sk.smartid.*;
import ee.sk.smartid.rest.dao.NationalIdentity;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmartIdSigner {

  private final Executor smartIdExecutor;
  private final SmartIdClient smartIdClient;
  private final MobileIDAuthenticator signer;

  public SmartIdSignatureSession sign(List<SignatureFile> files, String personalCode) {

    NationalIdentity nationalIdentity = new NationalIdentity("EE", personalCode);

    SmartIdCertificate certificateResponse =
        smartIdClient
            .getCertificate()
            .withNationalIdentity(nationalIdentity)
            .withCertificateLevel("QUALIFIED")
            .fetch();

    IdCardSignatureSession idCardSession;
    try {
      idCardSession =
          signer.startSign(
              files, Hex.encodeHexString(certificateResponse.getCertificate().getEncoded()));
    } catch (CertificateEncodingException e) {
      throw new RuntimeException(e);
    }

    SignableHash signableHash = new SignableHash();
    try {
      signableHash.setHash(Hex.decodeHex(idCardSession.hash.toCharArray()));
    } catch (DecoderException e) {
      throw new RuntimeException(e);
    }
    signableHash.setHashType(HashType.SHA256);

    SmartIdSignatureSession session =
        new SmartIdSignatureSession(signableHash.calculateVerificationCode(), idCardSession);

    smartIdExecutor.execute(
        () -> {
          String documentNumber = certificateResponse.getDocumentNumber();
          SmartIdSignature smartIdSignature =
              smartIdClient
                  .createSignature()
                  .withDocumentNumber(documentNumber)
                  .withSignableHash(signableHash)
                  .withCertificateLevel("QUALIFIED")
                  .sign();

          String signatureValue = Hex.encodeHexString(smartIdSignature.getValue());
          session.setSignedFile(signer.getSignedFile(idCardSession, signatureValue));
        });

    return session;
  }

  public byte[] getSignedFile(SmartIdSignatureSession session) {
    return session.getSignedFile();
  }
}
