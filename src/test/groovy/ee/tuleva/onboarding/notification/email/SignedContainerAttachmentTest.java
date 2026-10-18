package ee.tuleva.onboarding.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SignedContainerAttachmentTest {

  @Test
  void namesTheAttachmentAfterTheBaseNameAndEncodesTheContainer() {
    byte[] container = {1, 2, 3};

    MessageContent attachment = SignedContainerAttachment.of("tuleva_avaldus_1", container);

    MessageContent expected = new MessageContent();
    expected.setName("tuleva_avaldus_1.asice");
    expected.setType("application/vnd.etsi.asic-e+zip");
    expected.setContent(Base64.getEncoder().encodeToString(container));
    assertThat(attachment).usingRecursiveComparison().isEqualTo(expected);
  }
}
