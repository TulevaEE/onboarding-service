package ee.tuleva.onboarding.savings.fund;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "savings-fund")
@Getter
@Setter
public class SavingsFundConfiguration {
  private String isin = "EE0000003283";
}
