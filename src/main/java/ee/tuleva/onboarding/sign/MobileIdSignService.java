package ee.tuleva.onboarding.sign;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import com.codeborne.security.mobileid.SignatureFile;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MobileIdSignService {

	private final MobileIDAuthenticator signer;

	public MobileIdSignatureSession startSign(List<SignatureFile> files, String personalCode, String phone) {
		return signer.startSign(files, personalCode, phone);
	}

	public byte[] getSignedFile(MobileIdSignatureSession session) {
		return signer.getSignedFile(session);
	}
}
