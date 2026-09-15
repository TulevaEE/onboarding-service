package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSessionResponse;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionId;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.demoProperties;
import static ee.tuleva.onboarding.auth.smartid.SmartIdLoginStarter.CONCURRENT_LOGIN_STARTS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.tuleva.onboarding.auth.SmartIdProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(SmartIdLoginStarterConcurrencyLimitTest.ResilientConfig.class)
class SmartIdLoginStarterConcurrencyLimitTest {

  private static final CountDownLatch held = new CountDownLatch(1);
  private static final CountDownLatch allStartsInFlight =
      new CountDownLatch(CONCURRENT_LOGIN_STARTS);

  @Configuration
  @EnableResilientMethods
  static class ResilientConfig {

    @Bean
    SmartIdConnector connector() {
      SmartIdConnector connector = mock(SmartIdConnector.class);
      given(connector.initAnonymousDeviceLinkAuthentication(any()))
          .willAnswer(
              invocation -> {
                allStartsInFlight.countDown();
                held.await(5, SECONDS);
                return aDeviceLinkSessionResponse(aSessionId);
              });
      return connector;
    }

    @Bean
    SmartIdLoginStarter smartIdLoginStarter(SmartIdConnector connector) {
      var client = new SmartIdClient();
      client.setSmartIdConnector(connector);
      client.setRelyingPartyUUID(demoProperties.relyingPartyUUID());
      client.setRelyingPartyName(demoProperties.relyingPartyName());
      return new SmartIdLoginStarter(
          client, demoProperties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Bean
    SmartIdProperties smartIdProperties() {
      return demoProperties;
    }
  }

  @Autowired private SmartIdLoginStarter starter;

  @Test
  void turnsAwayALoginStartedWhileTheAllowedNumberAreAlreadyInFlight() throws Exception {
    try (ExecutorService starts = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < CONCURRENT_LOGIN_STARTS; i++) {
        starts.submit(() -> starter.startDeviceLinkLogin("et"));
      }
      assertThat(allStartsInFlight.await(5, SECONDS)).isTrue();

      assertThatThrownBy(() -> starter.startDeviceLinkLogin("et"))
          .isInstanceOf(InvocationRejectedException.class);

      held.countDown();
    }
  }
}
