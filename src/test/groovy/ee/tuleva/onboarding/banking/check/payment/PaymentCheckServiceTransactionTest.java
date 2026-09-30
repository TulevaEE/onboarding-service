package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_WITHOUT_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({PaymentCheckService.class, PaymentCheckServiceTransactionTest.FixedClockConfig.class})
class PaymentCheckServiceTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-28T09:00:00Z");
  private static final String ENTRY_KEY = "entry-123";
  private static final String DETAIL = "no matching redemption request";

  @Autowired private PaymentCheckService service;
  @Autowired private PaymentCheckEventRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private OperationsNotificationService notificationService;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void findingIsAnnouncedOnlyOnceItsTransactionCommits() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              recordTheFinding();
              verifyNoInteractions(notificationService);
            });

    verify(notificationService).sendMessage(any(), any());
  }

  @Test
  void findingInATransactionThatRollsBack_isNeitherKeptNorAnnounced() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              recordTheFinding();
              status.setRollbackOnly();
            });

    assertThat(repository.findAll()).isEmpty();
    verifyNoInteractions(notificationService);
  }

  @Test
  void slackFailureAfterTheFindingCommits_isRememberedAsUndelivered() {
    willThrow(new RestClientException("Slack unavailable"))
        .given(notificationService)
        .sendMessage(any(), any());

    assertThatCode(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> recordTheFinding()))
        .doesNotThrowAnyException();

    assertThat(repository.findByCheckTypeAndExternalKey(PAYOUT_WITHOUT_REQUEST, ENTRY_KEY))
        .get()
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(
            PaymentCheckEvent.builder()
                .checkType(PAYOUT_WITHOUT_REQUEST)
                .severity(HOLD)
                .externalKey(ENTRY_KEY)
                .detail(DETAIL)
                .alertFailed(true)
                .createdAt(NOW)
                .lastSeenAt(NOW)
                .build());
  }

  private void recordTheFinding() {
    service.record(PAYOUT_WITHOUT_REQUEST, HOLD, ENTRY_KEY, DETAIL);
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
