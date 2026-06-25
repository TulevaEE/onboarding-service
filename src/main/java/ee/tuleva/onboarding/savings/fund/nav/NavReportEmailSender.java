package ee.tuleva.onboarding.savings.fund.nav;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavReportEmailSender {

  private static final DateTimeFormatter SUBJECT_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter FILENAME_DATE_FORMAT =
      DateTimeFormatter.ofPattern("ddMMyyyy");

  private final NavReportCsvGenerator navReportCsvGenerator;
  private final EmailService emailService;

  boolean send(List<NavReportRow> rows, NavCalculationResult result) {
    return sendTo(rows, result, depositaryRecipients());
  }

  boolean sendForReview(List<NavReportRow> rows, NavCalculationResult result) {
    return sendTo(rows, result, reviewers());
  }

  private boolean sendTo(
      List<NavReportRow> rows, NavCalculationResult result, List<Recipient> recipients) {
    var csvBytes = navReportCsvGenerator.generate(rows);

    var fundCode = result.fund().getCode();
    var navDate = result.positionReportDate();
    var calculationDate = result.calculationDate();

    var message = new MandrillMessage();
    message.setFromEmail("funds@tuleva.ee");
    message.setFromName("Tuleva");
    message.setSubject(fundCode + " NAV arvutamine " + navDate.format(SUBJECT_DATE_FORMAT));
    message.setHtml(
        """
        <p>Tere,</p>
        <p>Manuses on %s fondi NAV arvutuse fail.</p>
        <p>See on automaatne kiri.</p>
        <p>Parimat,<br>Tuleva robot</p>
        """
            .formatted(fundCode));
    message.setTo(recipients);

    var attachment = new MessageContent();
    attachment.setName(
        fundCode + " NAV arvutamine " + calculationDate.format(FILENAME_DATE_FORMAT) + ".csv");
    attachment.setType("text/csv");
    attachment.setContent(Base64.getEncoder().encodeToString(csvBytes));
    message.setAttachments(List.of(attachment));

    boolean sent = emailService.sendSystemEmail(message);

    if (sent) {
      log.info("NAV report email sent: fund={}, date={}, rows={}", fundCode, navDate, rows.size());
    } else {
      log.error("NAV report email failed: fund={}, date={}", fundCode, navDate);
    }

    return sent;
  }

  private List<Recipient> reviewers() {
    return List.of(
        recipient("funds@tuleva.ee", TO),
        recipient("taavi.pertman@tuleva.ee", CC),
        recipient("compliance@tuleva.ee", CC),
        recipient("erko.risthein@tuleva.ee", BCC));
  }

  private List<Recipient> depositaryRecipients() {
    return Stream.concat(Stream.of(recipient("trustee@seb.ee", TO)), reviewers().stream()).toList();
  }

  private Recipient recipient(String email, Recipient.Type type) {
    var recipient = new Recipient();
    recipient.setEmail(email);
    recipient.setType(type);
    return recipient;
  }
}
