package ee.tuleva.onboarding.sign;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import com.codeborne.security.mobileid.MobileIdSignatureFile;
import com.codeborne.security.mobileid.MobileIdSignatureSession;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MobileIdSignService {

	private final MobileIDAuthenticator signer;

	public MobileIdSignatureSession startSignFiles(List<MobileIdSignatureFile> files, String personalCode, String phone) {
		MobileIdSignatureSession session = signer.startSignFiles(files, personalCode, phone);
		return session;
	}

	public byte[] getSignedFile(MobileIdSignatureSession session) {
		return signer.getSignedFile(session);
	}
}
