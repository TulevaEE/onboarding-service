package ee.tuleva.onboarding.notification.email.mailchimp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MailchimpCampaignSyncJob {

  private final MailchimpCampaignSyncService mailchimpCampaignSyncService;

  @Scheduled(cron = "0 0 13 * * *")
  public void syncCampaigns() {
    try {
      log.info("Starting scheduled Mailchimp campaign sync");
      mailchimpCampaignSyncService.syncLatestCampaign();
      log.info("Completed scheduled Mailchimp campaign sync");
    } catch (Exception e) {
      log.error("Failed to sync Mailchimp campaigns", e);
    }
  }
}
