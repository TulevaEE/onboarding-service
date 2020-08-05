package ee.tuleva.onboarding.mandate.signature;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SignatureService {

    private final SmartIdSigner smartIdSigner;
//    private final MobileIDAuthenticator signer;

    public SmartIdSignatureSession startSmartIdSign(List<SignatureFile> files, String personalCode) {
        return smartIdSigner.startSign(files, personalCode);
    }

    public byte[] getSignedFile(SmartIdSignatureSession session) {
        return smartIdSigner.getSignedFile(session);
    }

    public MobileIdSignatureSession startSign(List<SignatureFile> files, String personalCode, String phone) {
        throw new NotImplementedException("TODO");
//        return signer.startSign(files, personalCode, phone);
    }

    public byte[] getSignedFile(MobileIdSignatureSession session) {
        throw new NotImplementedException("TODO");
//        return signer.getSignedFile(session);
    }

    public IdCardSignatureSession startSign(List<SignatureFile> files, String signingCertificate) {
        throw new NotImplementedException("TODO");
//        return signer.startSign(files, signingCertificate);
    }

    public byte[] getSignedFile(IdCardSignatureSession session, String signedHash) {
        throw new NotImplementedException("TODO");
//        return signer.getSignedFile(session, signedHash);
    }
}
