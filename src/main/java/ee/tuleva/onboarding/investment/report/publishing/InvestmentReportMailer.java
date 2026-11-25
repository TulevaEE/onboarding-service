package ee.tuleva.onboarding.investment.report.publishing;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InvestmentReportMailer {

  private final EmailService emailService;

  boolean emailReports(YearMonth month, Map<FundReportMapping, byte[]> pdfs, List<String> errors) {
    try {
      var message = new MandrillMessage();
      message.setFromEmail("funds@tuleva.ee");
      message.setFromName("Tuleva");
      message.setSubject("Tuleva investeeringute aruanded " + month);
      message.setHtml(
          "<p>Tere,</p>"
              + "<p>Manuses on Tuleva pensionifondide investeeringute aruanded.</p>"
              + "<p>See on automaatne kiri.</p>"
              + "<p>Parimat,<br>Tuleva robot</p>");
      var recipient = new Recipient();
      recipient.setEmail("funds@tuleva.ee");
      recipient.setType(TO);
      message.setTo(List.of(recipient));
      message.setAttachments(emailAttachments(month, pdfs));
      return emailService.sendSystemEmail(message);
    } catch (Exception e) {
      log.error("Email send failed", e);
      errors.add("Email send failed: " + e.getMessage());
      return false;
    }
  }

  private static List<MessageContent> emailAttachments(
      YearMonth month, Map<FundReportMapping, byte[]> pdfs) {
    var attachments = new ArrayList<MessageContent>();
    for (var entry : pdfs.entrySet()) {
      if (entry.getKey().includeInEmail()) {
        var attachment = new MessageContent();
        attachment.setName(entry.getKey().buildPdfFilename(month));
        attachment.setType("application/pdf");
        attachment.setContent(Base64.getEncoder().encodeToString(entry.getValue()));
        attachments.add(attachment);
      }
    }
    return attachments;
  }

  private static String alertText(
      YearMonth month, Map<String, String> publishedUrls, PublishProblems problems) {
    var lines =
        new ArrayList<>(
            List.of(
                "Investment report publish for %s did not finish.".formatted(month),
                "Fund pages updated: " + publishedUrls.keySet(),
                "Errors: " + problems.lines()));
    var uploadsLeftUnlinked = problems.uploadsLeftUnlinked();
    if (!uploadsLeftUnlinked.isEmpty()) {
      lines.add(
          "Uploads this run made that no fund page links; link or delete each in the media"
              + " library: "
              + uploadsLeftUnlinked);
    }
    return String.join(System.lineSeparator(), lines);
  }

  void alertPartialPublish(
      YearMonth month, Map<String, String> publishedUrls, PublishProblems problems) {
    try {
      var message = new MandrillMessage();
      message.setFromEmail("funds@tuleva.ee");
      message.setFromName("Tuleva");
      message.setSubject("Investeeringute aruannete avaldamine jäi pooleli: " + month);
      message.setText(alertText(month, publishedUrls, problems));
      var recipient = new Recipient();
      recipient.setEmail("funds@tuleva.ee");
      recipient.setType(TO);
      message.setTo(List.of(recipient));
      emailService.sendSystemEmail(message);
    } catch (Exception e) {
      log.error("Failed to send partial-publish alert: {}", e.getMessage(), e);
    }
  }
}
