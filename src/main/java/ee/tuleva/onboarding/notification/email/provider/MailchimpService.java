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
import java.util.ArrayList;
import java.util.List;
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

  public List<SentToRecipient> getCampaignRecipients(String campaignId) {
    List<SentToRecipient> allRecipients = new ArrayList<>();
    int offset = 0;
    int count = 1000;

    while (true) {
      SentTo sentToPage =
          mailchimpMarketingReportsApi.getReportsIdSentTo(campaignId, null, null, count, offset);

      if (sentToPage.getSentTo() == null || sentToPage.getSentTo().isEmpty()) {
        break;
      }

      allRecipients.addAll(sentToPage.getSentTo());

      if (sentToPage.getSentTo().size() < count) {
        break;
      }

      offset += count;
    }

    log.info("Fetched {} recipients for campaign {}", allRecipients.size(), campaignId);
    return allRecipients;
  }

  public EmailActivity getCampaignActivity(String campaignId) {
    List<EmailActivityRecord> allEmailActivity = new ArrayList<>();
    int offset = 0;
    int count = 1000;

    while (true) {
      EmailActivity activityPage =
          mailchimpMarketingReportsApi.getReportsIdEmailActivity(
              campaignId, null, null, count, offset, null);

      if (activityPage.getEmails() == null || activityPage.getEmails().isEmpty()) {
        break;
      }

      allEmailActivity.addAll(activityPage.getEmails());

      if (activityPage.getEmails().size() < count) {
        break;
      }

      offset += count;
    }

    log.info(
        "Fetched {} email activity records for campaign {}", allEmailActivity.size(), campaignId);

    EmailActivity result = new EmailActivity();
    result.setEmails(allEmailActivity);
    return result;
  }

  public CampaignReport getCampaignReport(String campaignId) {
    return mailchimpMarketingReportsApi.getReportsId(campaignId, null, null);
  }
}
