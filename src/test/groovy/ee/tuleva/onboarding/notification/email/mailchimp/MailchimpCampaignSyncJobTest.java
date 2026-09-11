package ee.tuleva.onboarding.notification.email.mailchimp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class MailchimpCampaignSyncJobTest {

  private final MailchimpCampaignSyncService syncService = mock(MailchimpCampaignSyncService.class);
  private final MailchimpCampaignSyncJob job = new MailchimpCampaignSyncJob(syncService);

  @Test
  void syncsTheLatestCampaign() {
    job.syncCampaigns();

    verify(syncService).syncLatestCampaign();
  }

  @Test
  void doesNotPropagateAFailureFromTheSyncService() {
    willThrow(new RuntimeException("mailchimp down")).given(syncService).syncLatestCampaign();

    assertThatCode(job::syncCampaigns).doesNotThrowAnyException();
  }
}
