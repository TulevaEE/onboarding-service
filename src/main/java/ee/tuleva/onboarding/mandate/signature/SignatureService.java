package ee.tuleva.onboarding.mandate.signature;

import com.codeborne.security.mobileid.IdCardSignatureSession;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SignatureService {

	private final MobileIDAuthenticator signer;

	public MobileIdSignatureSession startSign(List<SignatureFile> files, String personalCode, String phone) {
		return signer.startSign(files, personalCode, phone);
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
