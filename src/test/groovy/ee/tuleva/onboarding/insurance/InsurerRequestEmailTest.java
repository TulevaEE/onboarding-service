package ee.tuleva.onboarding.insurance;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.insurance.InsuranceTransferFormFixture.signedForm;
import static ee.tuleva.onboarding.insurance.Insurer.ARTEA;
import static ee.tuleva.onboarding.insurance.Insurer.ERGO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsurerRequestEmailTest {

  private final User user = sampleUser().build();

  @Test
  void
      sendsTheSignedRequestToTheInsurerWithACopyToThePersonAndABlindCopyToTheApplicationsArchive() {
    MandrillMessage message = InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), user);

    assertThat(message.getTo())
        .extracting(Recipient::getEmail, Recipient::getType)
        .containsExactly(
            tuple("elukindlustus@ergo.ee", TO),
            tuple("jordan@example.com", CC),
            tuple("avaldused@tuleva.ee", BCC));
    assertThat(message.getPreserveRecipients()).isTrue();
  }

  @Test
  void letsTheInsurerReplyToThePersonAndTulevaAtOnce() {
    MandrillMessage message = InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), user);

    assertThat(message.getHeaders())
        .isEqualTo(Map.of("Reply-To", "jordan@example.com, tuleva@tuleva.ee"));
  }

  @Test
  void stillSendsTheRequestToTheInsurerWithRepliesToTulevaWhenThePersonHasNoEmail() {
    User withoutEmail = sampleUser().email(null).build();

    MandrillMessage message =
        InsurerRequestEmail.of(signedForm(1L, withoutEmail.getId(), ARTEA), withoutEmail);

    assertThat(message.getTo())
        .extracting(Recipient::getEmail, Recipient::getType)
        .containsExactly(tuple("kindlustus@artea.ee", TO), tuple("avaldused@tuleva.ee", BCC));
    assertThat(message.getHeaders()).isEqualTo(Map.of("Reply-To", "tuleva@tuleva.ee"));
  }

  @Test
  void comesFromTulevaWithThePersonsNameInTheSubjectAndTheSignedContainerAttached() {
    MandrillMessage message = InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), user);

    assertThat(message.getFromEmail()).isEqualTo("tuleva@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject())
        .isEqualTo("III samba kindlustuslepingu lõpetamise avaldus – Jordan Valdma");
    assertThat(message.getAttachments())
        .extracting(MessageContent::getName, MessageContent::getType)
        .containsExactly(tuple("Tuleva_Ergo_avaldus.asice", "application/vnd.etsi.asic-e+zip"));
  }

  @Test
  void namesThePersonTheFundAndThePensionAccountTheMoneyGoesTo() {
    MandrillMessage message = InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), user);

    assertThat(message.getHtml())
        .contains(
            "Jordan Valdma, isikukood 38812121215",
            "Tuleva III Samba Pensionifond (ISIN EE3600001707)",
            "Pensionikonto: 993432432",
            "Tuleva_Ergo_avaldus.asice");
  }

  @Test
  void escapesThePersonsNameInTheBody() {
    User withMarkupInName = sampleUser().firstName("<b>Jordan</b>").build();

    MandrillMessage message =
        InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), withMarkupInName);

    assertThat(message.getHtml()).contains("&lt;b&gt;Jordan&lt;/b&gt;").doesNotContain("<b>");
  }

  @Test
  void doesNotTrackTheInsurersOpensOrRewriteLinks() {
    MandrillMessage message = InsurerRequestEmail.of(signedForm(1L, user.getId(), ERGO), user);

    assertThat(List.of(message.getTrackOpens(), message.getTrackClicks()))
        .containsExactly(false, false);
  }
}
