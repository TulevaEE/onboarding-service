package ee.tuleva.onboarding.notification.email;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import java.util.Base64;

public final class SignedContainerAttachment {

  private SignedContainerAttachment() {}

  public static MessageContent of(String baseName, byte[] container) {
    MessageContent attachment = new MessageContent();
    attachment.setName(baseName + ".bdoc");
    attachment.setType("application/bdoc");
    attachment.setContent(Base64.getEncoder().encodeToString(container));
    return attachment;
  }
}
