package ee.tuleva.onboarding.config;

import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.MidSessionStatusPoller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MobileIdConfiguration {

  @Value("${smartid.relyingPartyUUID}")
  private String relyingPartyUUID;

  @Value("${smartid.relyingPartyName}")
  private String relyingPartyName;

  @Value("${mobile-id.hostUrl}")
  private String hostUrl;

  @Value("${mobile-id.pollingSleepTimeoutSeconds}")
  private int pollingSleepTimeoutSeconds;

  @Bean
  MidClient mobileIDClient() {
    return MidClient.newBuilder()
        .withRelyingPartyName(relyingPartyName)
        .withRelyingPartyUUID(relyingPartyUUID)
        .withHostUrl(hostUrl)
        .withPollingSleepTimeoutSeconds(pollingSleepTimeoutSeconds)
        .build();
  }

  @Bean
  MidConnector mobileIDConnector() {
    return mobileIDClient().getMobileIdConnector();
  }

  @Bean
  MidSessionStatusPoller mobileIDSessionStatusPoller() {
    return mobileIDClient().getSessionStatusPoller();
  }

  @Bean
  MidAuthenticationResponseValidator mobileIDValidator() {
    return new MidAuthenticationResponseValidator();
  }
}
