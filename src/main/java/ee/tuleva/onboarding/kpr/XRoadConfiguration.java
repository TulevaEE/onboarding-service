package ee.tuleva.onboarding.kpr;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "xroad")
@Getter
@Setter
public class XRoadConfiguration {

    private String kprEndpoint;
    private String instance;
    private String memberClass;
    private String memberCode;
    private String subsystemCode;
    private int requestTimeout;
    private int connectionTimeout;

}
