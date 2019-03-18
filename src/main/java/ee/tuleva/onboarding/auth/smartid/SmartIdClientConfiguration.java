package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.SmartIdClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
public class SmartIdClientConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "smartid")
    public SmartIdClient smartIdClient() {
        return new SmartIdClient();
    }

    @Bean
    public Executor smartIdExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
