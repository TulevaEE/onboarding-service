package ee.tuleva.onboarding.aml.notification;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.notification.slack.SlackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AmlCheckNotifierTest {

  private SlackService slackService;
  private AmlCheckNotifier notifier;

  @BeforeEach
  void setUp() {
    slackService = Mockito.mock(SlackService.class);
    notifier = new AmlCheckNotifier(slackService);
  }

  @Test
  @DisplayName("Should send Slack message when an AML check of a specific type has failed")
  void onAmlCheckCreated_inListAndFailed_sendsSlackMessage() {
    AmlCheckCreatedEvent event = mock(AmlCheckCreatedEvent.class);
    when(event.getAmlCheckType()).thenReturn(SANCTION);
    when(event.isFailed()).thenReturn(true);

    notifier.onAmlCheckCreated(event);

    verify(slackService).sendMessage("AML check failed: type=SANCTION");
  }

  @Test
  @DisplayName(
      "Should not send Slack message when the AML check is one of the listed types but succeeded")
  void onAmlCheckCreated_inListButNotFailed_noSlackMessage() {
    AmlCheckCreatedEvent event = mock(AmlCheckCreatedEvent.class);
    when(event.getAmlCheckType()).thenReturn(RISK_LEVEL);
    when(event.isFailed()).thenReturn(false);

    notifier.onAmlCheckCreated(event);

    verify(slackService, never()).sendMessage(anyString());
  }

  @Test
  @DisplayName(
      "Should not send Slack message when the AML check type is not one of the listed types, even if failed")
  void onAmlCheckCreated_notInListAndFailed_noSlackMessage() {
    AmlCheckCreatedEvent event = mock(AmlCheckCreatedEvent.class);
    when(event.getAmlCheckType()).thenReturn(OCCUPATION);
    when(event.isFailed()).thenReturn(true);

    notifier.onAmlCheckCreated(event);

    verify(slackService, never()).sendMessage(anyString());
  }

  @Test
  @DisplayName("Should always send Slack message when AML checks job is run")
  void onScheduledAmlCheckJobRun_sendsSlackMessage() {
    AmlChecksRunEvent event = mock(AmlChecksRunEvent.class);
    when(event.getNumberOfRecords()).thenReturn(10);

    notifier.onScheduledAmlCheckJobRun(event);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(slackService).sendMessage(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals(
        "Running AML checks job: numberOfRecords=10", captor.getValue());
  }

  @Test
  @DisplayName("Should always send Slack message with row counts after an AML risk level job run")
  void onAmlRiskLevelJobRun_sendsSlackMessage() {
    AmlRiskLevelJobRunEvent event = mock(AmlRiskLevelJobRunEvent.class);
    when(event.getHighRiskRowCount()).thenReturn(3);
    when(event.getAmlChecksCreatedCount()).thenReturn(2);

    notifier.onAmlRiskLevelJobRun(event);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(slackService).sendMessage(captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals(
        "Ran AML Risk Level job: highRiskRecordCount=3, amlChecksCreatedCount=2",
        captor.getValue());
  }
}
