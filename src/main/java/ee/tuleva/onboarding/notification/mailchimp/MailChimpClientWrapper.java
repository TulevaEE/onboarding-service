package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpClient;
import com.ecwid.maleorang.MailchimpException;
import com.ecwid.maleorang.MailchimpMethod;
import com.ecwid.maleorang.MailchimpObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Profile("production")
@RequiredArgsConstructor
public class MailChimpClientWrapper {

  private final MailchimpClient mailChimpClient;

  /**
   * A non-final method wrapper, so it could be easily mocked in tests
   */
  public <R extends MailchimpObject> R execute(MailchimpMethod<R> method) {
    try {
      return mailChimpClient.execute(method);
    } catch (IOException | MailchimpException e) {
      throw new MailChimpException(e);
    }
  }

  @Service
  @Profile({ "dev", "test" })
  @Slf4j
  public static class DevMailChimpClientWrapper extends MailChimpClientWrapper {

    public <R extends MailchimpObject> R execute(MailchimpMethod<R> method) {
      log.info("Not sending anything to Mailchimp in a non-production environment");
      return null; // do nothing
    }

    public DevMailChimpClientWrapper(MailchimpClient mailChimpClient) {
      super(mailChimpClient);
    }

  }

}
