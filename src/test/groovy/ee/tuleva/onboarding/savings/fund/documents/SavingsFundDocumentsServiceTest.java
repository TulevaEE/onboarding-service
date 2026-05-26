package ee.tuleva.onboarding.savings.fund.documents;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.MutableClock;
import org.junit.jupiter.api.Test;

class SavingsFundDocumentsServiceTest {

  private static final SavingsFundDocuments FETCHED =
      new SavingsFundDocuments(
          "https://tuleva.ee/wp-content/uploads/2026/06/terms.pdf",
          "https://tuleva.ee/wp-content/uploads/2026/06/prospectus.pdf",
          "https://tuleva.ee/wp-content/uploads/2026/06/key-info.pdf");

  private final WordpressDocumentsClient client = mock(WordpressDocumentsClient.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final MutableClock clock = new MutableClock();

  private final SavingsFundDocumentsService service =
      new SavingsFundDocumentsService(client, notificationService, clock);

  @Test
  void getDocuments_returnsBakedInFallback_beforeAnyRefresh() {
    assertThat(service.getDocuments()).isEqualTo(SavingsFundDocumentsService.FALLBACK);
  }

  @Test
  void refresh_onSuccess_updatesServedDocuments() {
    given(client.fetch()).willReturn(FETCHED);

    service.refresh();

    assertThat(service.getDocuments()).isEqualTo(FETCHED);
  }

  @Test
  void refresh_onFailure_keepsLastKnownGood_andDoesNotThrow() {
    given(client.fetch()).willReturn(FETCHED);
    service.refresh();

    given(client.fetch()).willThrow(new WordpressDocumentsException("WordPress down"));

    assertThatCode(service::refresh).doesNotThrowAnyException();
    assertThat(service.getDocuments()).isEqualTo(FETCHED);
  }

  @Test
  void refresh_onFailure_alertsOperations() {
    given(client.fetch()).willThrow(new WordpressDocumentsException("WordPress down"));

    service.refresh();

    verify(notificationService, times(1)).sendMessage(anyString(), eq(SAVINGS));
  }

  @Test
  void refresh_onFailure_doesNotThrow_evenWhenAlertSendItselfThrows() {
    given(client.fetch()).willThrow(new WordpressDocumentsException("WordPress down"));
    willThrow(new RuntimeException("Slack down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(SAVINGS));

    assertThatCode(service::refresh).doesNotThrowAnyException();
    assertThat(service.getDocuments()).isEqualTo(SavingsFundDocumentsService.FALLBACK);
  }

  @Test
  void refresh_whenStuckOnFallbackBeyondThreshold_alsoSendsStalenessAlert() {
    given(client.fetch()).willThrow(new WordpressDocumentsException("WordPress down"));

    clock.tick(49, HOURS);
    service.refresh();

    verify(notificationService, times(2)).sendMessage(anyString(), eq(SAVINGS));
  }
}
