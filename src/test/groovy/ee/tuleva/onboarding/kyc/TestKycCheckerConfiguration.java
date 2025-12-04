package ee.tuleva.onboarding.kyc;

import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestKycCheckerConfiguration {

  @Bean
  @Primary
  public TestKycChecker testKycChecker() {
    return new TestKycChecker(new KycCheck(LOW, Map.of()));
  }
}
