package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.AtMostOnceEmailSender;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  CustodianOrderEmailSender.class,
  CustodianOrderMessageFactory.class,
  CustodianOrderEmailNotifier.class,
  CustodianOrderEmailAlerter.class,
  CustodianOrderEmailNotifierIT.OrderEmailSettings.class
})
class CustodianOrderEmailNotifierIT {

  private static final Instant SENT_AT = Instant.parse("2026-01-15T10:00:00Z");
  private static final Map<String, byte[]> EXPORTS = Map.of("sebFundXlsx", new byte[] {1, 2});
  private static final String ORDERS_NOT_SENT =
      "🔴 Orders NOT sent: TUV100 ostu-/müügikorraldused 15.01.2026\n"
          + "ConnectException: Connection refused\n"
          + "Nothing went out to the desk. Fix the cause, then send the orders again.";

  @Autowired CustodianOrderEmailSender sender;
  @Autowired CustodianOrderEmailAlerter alerter;
  @Autowired EventLogRepository eventLogRepository;
  @Autowired PlatformTransactionManager transactionManager;

  @MockitoBean AtMostOnceEmailSender orderEmailTransport;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean Clock clock;

  @TestConfiguration
  static class OrderEmailSettings {
    @Bean
    CustodianOrderEmailProperties custodianOrderEmailProperties() {
      return new CustodianOrderEmailProperties(true, List.of("desk@example.com"), List.of());
    }
  }

  @BeforeEach
  void setUp() {
    eventLogRepository.deleteAll();
    given(clock.instant()).willReturn(SENT_AT);
  }

  @AfterEach
  void cleanUp() {
    eventLogRepository.deleteAll();
  }

  @Test
  void aFailedSendWhoseFirstSlackPostFailsIsPostedByALaterRun_andTheEmailIsNotSentAgain() {
    given(orderEmailTransport.send(any()))
        .willReturn(new EmailSendResult(FAILED, List.of(), "ConnectException: Connection refused"));
    willThrow(new RestClientException("Slack is down"))
        .willDoNothing()
        .given(notificationService)
        .sendMessage(ORDERS_NOT_SENT, TRADING, ERROR);

    sendAfterTheBatchCommits();

    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(sendResult(SENT_AT, "NOT_POSTED"));
    then(notificationService).shouldHaveNoInteractions();

    runAt(SENT_AT.plus(Duration.ofMinutes(1)));

    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(sendResult(SENT_AT, "NOT_POSTED"));

    runAt(SENT_AT.plus(Duration.ofMinutes(2)));
    runAt(SENT_AT.plus(Duration.ofMinutes(3)));

    then(notificationService).should(times(2)).sendMessage(ORDERS_NOT_SENT, TRADING, ERROR);
    then(notificationService).shouldHaveNoMoreInteractions();
    then(orderEmailTransport).should().send(any());
    then(orderEmailTransport).shouldHaveNoMoreInteractions();
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(sendResult(SENT_AT, "POSTED"));
  }

  private void sendAfterTheBatchCommits() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status ->
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        sender.send(TUV100, SENT_AT, EXPORTS);
                      }
                    }));
  }

  private void runAt(Instant now) {
    given(clock.instant()).willReturn(now);
    alerter.postAlertsNotYetPosted();
  }

  private static Consumer<EventLog> sendResult(Instant at, String alert) {
    return row -> {
      assertThat(row.getType()).isEqualTo("TRADE_ORDER_EMAIL_SEND_RESULT");
      assertThat(row.getTimestamp()).isEqualTo(at);
      assertThat(row.getData())
          .isEqualTo(Map.of("text", ORDERS_NOT_SENT, "severity", "ERROR", "alert", alert));
    };
  }
}
