package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent.TRADE_ORDER_TAG;
import static ee.tuleva.onboarding.savings.fund.nav.TrusteeEmailFailureAlerter.TRUSTEE_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.mandate.email.webhook.MandrillSignatureVerifier;
import ee.tuleva.onboarding.mandate.email.webhook.MandrillWebhookService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  MandrillWebhookService.class,
  TrusteeEmailFailureAlerter.class,
  TrusteeEmailFailureAlerterIT.OrderEmailFailureAlerterAndWebhookJson.class
})
class TrusteeEmailFailureAlerterIT {

  private static final Instant BOUNCED_AT = Instant.parse("2026-06-15T15:20:00Z");
  private static final String SIGNATURE = "valid-signature";

  @Autowired MandrillWebhookService webhookService;
  @Autowired EventLogRepository eventLogRepository;

  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean MandrillSignatureVerifier signatureVerifier;
  @MockitoBean EmailPersistenceService emailPersistenceService;
  @MockitoBean Clock clock;

  @TestConfiguration
  @ComponentScan(
      basePackages = "ee.tuleva.onboarding.investment.transaction.export",
      useDefaultFilters = false,
      includeFilters =
          @Filter(type = FilterType.REGEX, pattern = ".*\\.CustodianOrderEmailFailureAlerter"))
  static class OrderEmailFailureAlerterAndWebhookJson {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @BeforeEach
  void setUp() {
    eventLogRepository.deleteAll();
    given(signatureVerifier.verify(any(), eq(SIGNATURE))).willReturn(true);
  }

  @AfterEach
  void cleanUp() {
    eventLogRepository.deleteAll();
  }

  @Test
  void anOrderEmailFailureAddressedToTheTrusteeIsOnlyRecordedForTheTradingChannel_withoutSlack() {
    webhookReceives(bounceTo(TRUSTEE_EMAIL, "msg_1", "\"" + TRADE_ORDER_TAG + "\""));

    assertThat(eventLogRepository.findAll())
        .extracting(EventLog::getType, row -> row.getData().get("alert"))
        .containsExactly(tuple("TRADE_ORDER_EMAIL_HARD_BOUNCE", "NOT_POSTED"));
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anOrdinaryTrusteeEmailFailureIsStillAlertedAtOnceInTheSavingsChannel() {
    webhookReceives(bounceTo(TRUSTEE_EMAIL, "msg_2", ""));

    assertThat(eventLogRepository.findAll())
        .extracting(EventLog::getType, EventLog::getPrincipal)
        .containsExactly(tuple("HARD_BOUNCE", "msg_2"));
    then(notificationService).should().sendMessage(anyString(), eq(SAVINGS));
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  private void webhookReceives(String mandrillEvents) {
    var request = new MockHttpServletRequest("POST", "/v1/emails/webhooks/mandrill");
    request.addParameter("mandrill_events", mandrillEvents);
    webhookService.handleWebhook(mandrillEvents, SIGNATURE, request);
  }

  private static String bounceTo(String recipient, String mandrillMessageId, String tags) {
    return """
        [{"event": "hard_bounce", "ts": %d, "msg": {"_id": "%s", "email": "%s",
          "subject": "TKF100 NAV", "bounce_description": "550 mailbox unavailable",
          "tags": [%s]}}]
        """
        .formatted(BOUNCED_AT.getEpochSecond(), mandrillMessageId, recipient, tags);
  }
}
