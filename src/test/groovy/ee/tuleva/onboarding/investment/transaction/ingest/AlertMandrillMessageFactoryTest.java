package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertMandrillMessageFactoryTest {

  private final AlertProperties properties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));
  private final AlertMandrillMessageFactory factory = new AlertMandrillMessageFactory(properties);

  @Test
  void create_setsSubjectAndPlaintextBody() {
    MandrillMessage message = factory.create("subject line", "plain body");

    assertThat(message.getSubject()).isEqualTo("subject line");
    assertThat(message.getText()).isEqualTo("plain body");
    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
  }

  @Test
  void create_doesNotEnableAutoHtmlSoSebFieldsCannotBeAutoLinkified() {
    MandrillMessage message = factory.create("subject line", "https://phish.example/ click");

    assertThat(message.getAutoHtml()).isNull();
    assertThat(message.getHtml()).isNull();
  }

  @Test
  void create_buildsToAndCcRecipients() {
    MandrillMessage message = factory.create("subject", "body");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);
  }
}
