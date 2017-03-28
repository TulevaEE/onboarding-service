package ee.tuleva.onboarding.config;

import com.tapstream.rollbar.RollbarFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RollbarConfiguration {

    @Bean
    public RollbarFilter rollbarFilter() {
        return new RollbarFilter();
    }

}
