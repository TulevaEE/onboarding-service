package ee.tuleva.onboarding.notification.mailchimp;

import com.ecwid.maleorang.MailchimpClient;
import com.ecwid.maleorang.MailchimpException;
import com.ecwid.maleorang.MailchimpMethod;
import com.ecwid.maleorang.MailchimpObject;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MailChimpClientWrapper {

  private final MailchimpClient mailChimpClient;

  /**
   * A non-final method wrapper, so it could be easily mocked in tests
   */
  @NotNull
  public <R extends MailchimpObject> R execute(@NotNull MailchimpMethod<R> method) {
    try {
      return mailChimpClient.execute(method);
    } catch (IOException | MailchimpException e) {
      throw new MailChimpException(e);
    }
  }

}
