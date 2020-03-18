package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SignatureService {

  private final MobileIDAuthenticator signer;
  private final SmartIdSigner smartIdSigner;

  public MobileIdSignatureSession startSign(
      List<SignatureFile> files, String personalCode, String phone) {
    return signer.startSign(files, personalCode, phone);
  }

  public SmartIdSignatureSession startSmartIdSign(List<SignatureFile> files, String personalCode) {
    return smartIdSigner.sign(files, personalCode);
  }

  public byte[] getSignedFile(SmartIdSignatureSession session) {
    return smartIdSigner.getSignedFile(session);
  }

  public byte[] getSignedFile(MobileIdSignatureSession session) {
    return signer.getSignedFile(session);
  }

  public IdCardSignatureSession startSign(List<SignatureFile> files, String signingCertificate) {
    return signer.startSign(files, signingCertificate);
  }

  public byte[] getSignedFile(IdCardSignatureSession session, String signedHash) {
    return signer.getSignedFile(session, signedHash);
  }
}
