package ee.tuleva.onboarding.notification.email.provider;

import ee.tuleva.onboarding.notification.email.auto.EmailEvent;
import io.github.erkoristhein.mailchimp.api.MessagesApi;
import io.github.erkoristhein.mailchimp.marketing.api.CampaignsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ListsApi;
import io.github.erkoristhein.mailchimp.marketing.api.ReportsApi;
import io.github.erkoristhein.mailchimp.marketing.model.Campaign;
import io.github.erkoristhein.mailchimp.marketing.model.CampaignReport;
import io.github.erkoristhein.mailchimp.marketing.model.EmailActivity;
import io.github.erkoristhein.mailchimp.marketing.model.EmailActivityRecord;
import io.github.erkoristhein.mailchimp.marketing.model.Events;
import io.github.erkoristhein.mailchimp.marketing.model.GetCampaigns200Response;
import io.github.erkoristhein.mailchimp.marketing.model.SentTo;
import io.github.erkoristhein.mailchimp.marketing.model.SentToRecipient;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendRequestMessageToInner;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendTemplateRequest;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendTemplateRequestMessage;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailchimpService {

  private final MessagesApi mailchimpTransactionalMessagesApi;
  private final ListsApi mailchimpMarketingListsApi;
  private final CampaignsApi mailchimpMarketingCampaignsApi;
  private final ReportsApi mailchimpMarketingReportsApi;

  @Value("${mailchimp.listId}")
  private String mailchimpListId;

  public void sendEvent(String email, EmailEvent emailEvent) {
    Events event = new Events().name(emailEvent.name().toLowerCase());
    mailchimpMarketingListsApi.postListMemberEvents(mailchimpListId, email, event);
  }

  // TODO: Not implemented
  public void sendMessage() {
    PostMessagesSendTemplateRequestMessage message =
        new PostMessagesSendTemplateRequestMessage()
            .to(List.of(new PostMessagesSendRequestMessageToInner().email("test")));
    PostMessagesSendTemplateRequest body =
        new PostMessagesSendTemplateRequest().key("key_example").message(message);
    mailchimpTransactionalMessagesApi.postMessagesSendTemplate(body);
  }

  public Campaign getLatestSentCampaign() {
    GetCampaigns200Response campaigns =
        mailchimpMarketingCampaignsApi.getCampaigns(
            null,
            null,
            1,
            0,
            null,
            "sent",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "send_time",
            "DESC",
            null);

    if (campaigns.getCampaigns() != null && !campaigns.getCampaigns().isEmpty()) {
      return campaigns.getCampaigns().get(0);
    }

    log.warn("No sent campaigns found");
    return null;
  }

  public void processCampaignRecipients(
      String campaignId, Consumer<List<SentToRecipient>> pageProcessor) {
    int offset = 0;
    int count = 1000;
    int totalProcessed = 0;

    while (true) {
      SentTo sentToPage =
          mailchimpMarketingReportsApi.getReportsIdSentTo(campaignId, null, null, count, offset);

      if (sentToPage.getSentTo() == null || sentToPage.getSentTo().isEmpty()) {
        break;
      }

      pageProcessor.accept(sentToPage.getSentTo());
      totalProcessed += sentToPage.getSentTo().size();

      if (sentToPage.getSentTo().size() < count) {
        break;
      }

      offset += count;
    }

    log.info("Processed {} recipients for campaign: campaignId={}", totalProcessed, campaignId);
  }

  public void processCampaignActivity(
      String campaignId, Consumer<List<EmailActivityRecord>> pageProcessor) {
    int offset = 0;
    int count = 1000;
    int totalProcessed = 0;

    while (true) {
      EmailActivity activityPage =
          mailchimpMarketingReportsApi.getReportsIdEmailActivity(
              campaignId, null, null, count, offset, null);

      if (activityPage.getEmails() == null || activityPage.getEmails().isEmpty()) {
        break;
      }

      pageProcessor.accept(activityPage.getEmails());
      totalProcessed += activityPage.getEmails().size();

      if (activityPage.getEmails().size() < count) {
        break;
      }

      offset += count;
    }

    log.info(
        "Processed {} email activity records for campaign: campaignId={}",
        totalProcessed,
        campaignId);
  }

  public CampaignReport getCampaignReport(String campaignId) {
    return mailchimpMarketingReportsApi.getReportsId(campaignId, null, null);
  }
}
