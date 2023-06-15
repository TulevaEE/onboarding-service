package ee.tuleva.onboarding.config;

import com.microtripit.mandrillapp.lutung.MandrillApi;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@Getter
public class EmailConfiguration {

  @Value("${mandrill.key:#{null}}")
  private String mandrillKey;

  private Optional<String> mandrillKey() {
    return Optional.ofNullable(mandrillKey);
  }

  @Bean
  public MandrillApi mandrillApi() {

    if (!mandrillKey().isPresent()) {
      log.warn("Mandrill key not present.");
    }

    return mandrillKey().map(MandrillApi::new).orElse(null);
  }
}
