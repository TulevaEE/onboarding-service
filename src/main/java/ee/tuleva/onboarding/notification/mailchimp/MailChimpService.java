package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.method.v3_0.lists.members.EditMemberMethod;
import com.ecwid.maleorang.method.v3_0.lists.members.MemberInfo;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Future;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailChimpService {

  private final MailChimpClientWrapper mailChimpClient;

  @Value("${mailchimp.list.id}")
  private String listId;

  @Async
  public Future<MemberInfo> createOrUpdateMember(User user) {
    log.info("Creating or updating Mailchimp member in a separate thread");

    EditMemberMethod.CreateOrUpdate method = new EditMemberMethod.CreateOrUpdate(listId, user.getEmail());

    method.status = "subscribed";
    method.merge_fields = new MailchimpObject();

    Map<String, Object> mergeFields = method.merge_fields.mapping;
    mergeFields.put("FNAME", user.getFirstName());
    mergeFields.put("LNAME", user.getLastName());
    mergeFields.put("ISIKUKOOD", user.getPersonalCode());
    mergeFields.put("TELEFON", user.getPhoneNumber());
    if(user.getMember().isPresent()) {
      mergeFields.put("LIIKME_NR", user.getMemberOrThrow().getMemberNumber());
    }

    MemberInfo memberInfo = mailChimpClient.execute(method);
    return new AsyncResult<>(memberInfo);
  }

}
