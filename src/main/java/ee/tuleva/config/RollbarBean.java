package ee.tuleva.config;

import com.rollbar.Rollbar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RollbarBean {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	@Value("${rollbar.accessToken}")
	private String accessToken;

	@Value("${rollbar.profile}")
	private String profile;


	@Bean
	public Rollbar rollbar() {
		log.info("Setting up rollbar");
		Rollbar rollbar = new Rollbar(accessToken, profile);
		rollbar.handleUncaughtErrors();
		return rollbar;
	}

}