package ee.tuleva.onboarding.sign;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIdSignatureFile;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MobileIdSignService {

	private final MobileIDAuthenticator signer;

	public String startSign(MobileIdSignatureFile file, String personalCode, String phone) {
		MobileIdSignatureSession session = signer.startSign(file, personalCode, phone);
		return session.challenge;
	}

	public byte[] getSignedFile(MobileIdSignatureSession session) {
		return signer.getSignedFile(session);
	}
}
