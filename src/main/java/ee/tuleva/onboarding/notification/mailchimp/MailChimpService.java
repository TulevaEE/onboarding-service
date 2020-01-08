package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.method.v3_0.lists.members.EditMemberMethod;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;


@Service
@Slf4j
@RequiredArgsConstructor
public class MailChimpService {

  private final MailChimpClientWrapper mailChimpClient;

  @Value("${mailchimp.list.id}")
  private String listId;

  @Async
  public void createOrUpdateMember(User user) {
    log.info("Creating or updating Mailchimp member in a separate thread: {}", user);

    if (isBlank(user.getEmail())) {
      return;
    }

    EditMemberMethod.CreateOrUpdate method = new EditMemberMethod.CreateOrUpdate(listId, user.getEmail());

    method.status = "subscribed"; // TODO: maybe the user has manually unsubscribed?
    method.merge_fields = new MailchimpObject();

    Map<String, Object> mergeFields = method.merge_fields.mapping;
    mergeFields.put("FNAME", user.getFirstName());
    mergeFields.put("LNAME", user.getLastName());
    mergeFields.put("ISIKUKOOD", user.getPersonalCode());
    mergeFields.put("TELEFON", user.getPhoneNumber());
    if(user.getMember().isPresent()) {
      mergeFields.put("LIIKME_NR", user.getMemberOrThrow().getMemberNumber());
    }

    try {
      mailChimpClient.execute(method);
    } catch(MailChimpException e) {
      log.error("Error updating user in Mailchimp", e);
    }
  }

}
