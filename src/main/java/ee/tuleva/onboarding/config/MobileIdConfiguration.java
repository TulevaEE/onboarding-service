package ee.tuleva.onboarding.config;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MobileIdConfiguration {

	@Value("${digidoc.service.url}")
	private String digidocServiceUrl;

	@Value("${keystore.path}")
	private String keystorePath;

	@Value("${mobile-id.service.name}")
	private String serviceName;

	@Bean
	MobileIDAuthenticator mobileIDAuthenticator() {
		System.setProperty("javax.net.ssl.trustStore", keystorePath);
		return new MobileIDAuthenticator(digidocServiceUrl, serviceName);
	}

}
