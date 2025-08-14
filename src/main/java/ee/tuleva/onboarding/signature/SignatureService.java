package ee.tuleva.onboarding.signature;

import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.idcard.IdCardSigner;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSigner;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.smartid.SmartIdSigner;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SignatureService {

  private final SmartIdSigner smartIdSigner;
  private final MobileIdSigner mobileIdSigner;
  private final IdCardSigner idCardSigner;

  public SmartIdSignatureSession startSmartIdSign(List<SignatureFile> files, String personalCode) {
    return smartIdSigner.startSign(files, personalCode);
  }

  public byte[] getSignedFile(SmartIdSignatureSession session) {
    return smartIdSigner.getSignedFile(session);
  }

  public MobileIdSignatureSession startMobileIdSign(
      List<SignatureFile> files, String personalCode, String phoneNumber) {
    return mobileIdSigner.startSign(files, personalCode, phoneNumber);
  }

  public byte[] getSignedFile(MobileIdSignatureSession session) {
    return mobileIdSigner.getSignedFile(session);
  }

  public IdCardSignatureSession startIdCardSign(
      List<SignatureFile> files, String signingCertificate) {
    return idCardSigner.startSign(files, signingCertificate);
  }

  public byte[] getSignedFile(IdCardSignatureSession session, String signedHashInHex) {
    return idCardSigner.getSignedFile(session, signedHashInHex);
  }
}
