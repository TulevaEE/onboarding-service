package ee.tuleva.onboarding.config;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MobileIdConfiguration {

	private static final String TEST_DIGIDOC_SERVICE_URL = "https://tsp.demo.sk.ee/";
	private static final String KEYSTORE_PATH = "test_keys/keystore.jks";

	@Bean
	MobileIDAuthenticator mobileIDAuthenticator() {
		System.setProperty("javax.net.ssl.trustStore", KEYSTORE_PATH);
		return new MobileIDAuthenticator(TEST_DIGIDOC_SERVICE_URL);
	}

}
