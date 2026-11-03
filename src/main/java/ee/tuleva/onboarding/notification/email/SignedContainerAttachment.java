package ee.tuleva.onboarding.notification.email;

import static ee.tuleva.onboarding.signature.SignatureFile.SignatureFileType.DIGIDOC_CONTAINER;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import java.util.Base64;

public final class SignedContainerAttachment {

  private SignedContainerAttachment() {}

  public static MessageContent of(String baseName, byte[] container) {
    MessageContent attachment = new MessageContent();
    attachment.setName(DIGIDOC_CONTAINER.fileName(baseName));
    attachment.setType(DIGIDOC_CONTAINER.getMimeType());
    attachment.setContent(Base64.getEncoder().encodeToString(container));
    return attachment;
  }
}
