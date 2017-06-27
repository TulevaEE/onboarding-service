package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.method.v3_0.lists.members.EditMemberMethod;
import com.ecwid.maleorang.method.v3_0.lists.members.MemberInfo;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailChimpService {

  private final MailChimpClientWrapper mailChimpClient;

  @Value("${mailchimp.list.id}")
  private String listId;

  public MemberInfo createOrUpdateMember(User user) {
    EditMemberMethod.CreateOrUpdate method = new EditMemberMethod.CreateOrUpdate(listId, user.getEmail());
    method.status = "subscribed";
    method.merge_fields = new MailchimpObject();
    Map<String, Object> mergeFields = method.merge_fields.mapping;

    mergeFields.put("FNAME", user.getFirstName());
    mergeFields.put("LNAME", user.getLastName());
    mergeFields.put("ISIKUKOOD", user.getPersonalCode());
    mergeFields.put("TELEFON", user.getPhoneNumber());

    return mailChimpClient.execute(method);
  }

}
