package ee.tuleva.onboarding.auth.idcard;

import com.codeborne.security.mobileid.CheckCertificateResponse;
import com.codeborne.security.mobileid.MobileIDAuthenticator;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class IdCardAuthService {

	private final MobileIDAuthenticator authenticator;
	private final GenericSessionStore sessionStore;

	public IdCardSession checkCertificate(String certificate) {
		log.info("Checking ID card certificate");
		CheckCertificateResponse response = authenticator.checkCertificate(certificate);
		IdCardSession session = IdCardSession.builder()
				.firstName(response.firstName)
				.lastName(response.lastName)
				.personalCode(response.personalCode)
				.build();
		sessionStore.save(session);
		return session;
	}

}
