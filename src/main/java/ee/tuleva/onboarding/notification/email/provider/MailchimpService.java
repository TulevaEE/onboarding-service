package ee.tuleva.onboarding.notification.email.provider;

import ee.tuleva.onboarding.notification.email.auto.EmailEvent;
import io.github.erkoristhein.mailchimp.api.MessagesApi;
import io.github.erkoristhein.mailchimp.marketing.api.ListsApi;
import io.github.erkoristhein.mailchimp.marketing.model.Events;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendRequestMessageToInner;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendTemplateRequest;
import io.github.erkoristhein.mailchimp.model.PostMessagesSendTemplateRequestMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailchimpService {

  private final MessagesApi mailchimpTransactionalMessagesApi;
  private final ListsApi mailchimpMarketingListsApi;

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
}
