package ee.tuleva.onboarding.insurance;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static java.util.Objects.requireNonNull;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.notification.email.SignedContainerAttachment;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class InsurerRequestEmail {

  static final String TULEVA = "tuleva@tuleva.ee";
  static final String APPLICATIONS_ARCHIVE = "avaldused@tuleva.ee";
  private static final String TULEVA_THIRD_PILLAR_FUND_ISIN = "EE3600001707";

  private InsurerRequestEmail() {}

  static MandrillMessage of(InsuranceTransferForm form, User user) {
    Insurer insurer = form.getInsurer();
    String attachmentName = insurer.signedFileBaseName() + ".asice";
    @Nullable String personEmail = emailOf(user);

    var message = new MandrillMessage();
    message.setFromEmail(TULEVA);
    message.setFromName("Tuleva");
    message.setSubject("III samba kindlustuslepingu lõpetamise avaldus – " + user.getFullName());
    message.setHtml(body(user, form.getPensionAccountNumber(), attachmentName));
    message.setAutoText(true);
    message.setTo(recipients(insurer.getEmail(), personEmail));
    message.setPreserveRecipients(true);
    message.setHeaders(Map.of("Reply-To", replyTo(personEmail)));
    message.setImportant(true);
    message.setTrackOpens(false);
    message.setTrackClicks(false);
    message.setTags(List.of("insurance_transfer"));
    message.setAttachments(
        List.of(
            SignedContainerAttachment.of(
                insurer.signedFileBaseName(),
                requireNonNull(
                    form.getSignedContainer(),
                    "Signed insurance transfer form missing: formId=" + form.getId()))));
    return message;
  }

  private static String body(User user, String pensionAccountNumber, String attachmentName) {
    return """
        <p>Tere</p>
        <p>Edastame kliendi palvel tema digitaalselt allkirjastatud avalduse III samba \
        kindlustuslepingu lõpetamiseks ja raha kandmiseks Tuleva III Samba Pensionifondi. \
        Avaldus on manuses (%s).</p>
        <p>Klient: %s, isikukood %s<br>
        Pensionifond: Tuleva III Samba Pensionifond (ISIN %s)<br>
        Pensionikonto: %s</p>
        <p>Kliendil on selle kirja koopia. Kui vastate, jõuab vastus kliendile ja Tulevale.</p>
        <p>Lugupidamisega<br>
        Tuleva<br>
        %s · 644 5100</p>
        """
        .formatted(
            htmlEscape(attachmentName),
            htmlEscape(user.getFullName()),
            htmlEscape(user.getPersonalCode()),
            TULEVA_THIRD_PILLAR_FUND_ISIN,
            htmlEscape(pensionAccountNumber),
            TULEVA);
  }

  private static List<Recipient> recipients(String insurerEmail, @Nullable String personEmail) {
    var recipients = new ArrayList<Recipient>();
    recipients.add(recipient(insurerEmail, TO));
    if (personEmail != null) {
      recipients.add(recipient(personEmail, CC));
    }
    recipients.add(recipient(APPLICATIONS_ARCHIVE, BCC));
    return recipients;
  }

  private static String replyTo(@Nullable String personEmail) {
    return personEmail == null ? TULEVA : personEmail + ", " + TULEVA;
  }

  private static @Nullable String emailOf(User user) {
    @Nullable String email = user.getEmail();
    return email == null || email.isBlank() ? null : email.strip();
  }

  private static Recipient recipient(String email, Recipient.Type type) {
    var recipient = new Recipient();
    recipient.setEmail(email);
    recipient.setType(type);
    return recipient;
  }
}
