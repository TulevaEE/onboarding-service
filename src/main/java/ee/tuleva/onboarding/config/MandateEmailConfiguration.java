package ee.tuleva.onboarding.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class MandateEmailConfiguration {

    @Value("${mandate.email.from}")
    private String from;

    @Value("${mandate.email.to}")
    private String to;

    @Value("${mandate.email.bcc}")
    private String bcc;

    @Value("${mandrill.key:}")
    private String mandrillKey;

}
