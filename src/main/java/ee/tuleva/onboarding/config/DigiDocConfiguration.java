package ee.tuleva.onboarding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DigiDocConfiguration {

    @Bean
    @Profile("production")
    public org.digidoc4j.Configuration digiDocConfigProd() {
        return new org.digidoc4j.Configuration(org.digidoc4j.Configuration.Mode.PROD);
    }

    @Bean
    @Profile("dev")
    public org.digidoc4j.Configuration digiDocConfigDev() {
        return new org.digidoc4j.Configuration(org.digidoc4j.Configuration.Mode.TEST);
    }

}
