package ee.tuleva.onboarding.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@Getter
public class MandateEmailConfiguration {

    @Value("${mandate.email.from}")
    private String from;

    @Value("${mandate.email.bcc}")
    private String bcc;

    @Value("${mandrill.key:#{null}}")
    private String mandrillKey;

    public Optional<String> getMandrillKey() {
        return Optional.ofNullable(mandrillKey);
    }

}
