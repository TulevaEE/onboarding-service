package ee.tuleva.onboarding.config;

import com.codeborne.security.mobileid.MobileIDAuthenticator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MobileIdConfiguration {

	@Value("${digidoc.service.url}")
	private String digidocServiceUrl;

	@Value("${truststore.path}")
	private String trustStorePath;

	@Value("${mobile-id.service.name}")
	private String serviceName;

	@Bean
	MobileIDAuthenticator mobileIDAuthenticator() {
		System.setProperty("javax.net.ssl.trustStore", trustStorePath);
		log.info("Setting global ssl truststore to {}", this.trustStorePath);
		log.info("setting digidoc service url to {} with name {}", this.digidocServiceUrl, this.serviceName);
		return new MobileIDAuthenticator(digidocServiceUrl, serviceName);
	}

}
