package ee.tuleva.onboarding.savings.fund.nav;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
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

  private final NavReportMapper navReportMapper;
  private final NavReportCsvGenerator navReportCsvGenerator;
  private final EmailService emailService;

  void send(NavCalculationResult result) {
    var rows = navReportMapper.map(result);
    var csvBytes = navReportCsvGenerator.generate(rows);

    var fundCode = result.fund().getCode();
    var navDate = result.positionReportDate();

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

    var funds = new Recipient();
    funds.setEmail("funds@tuleva.ee");

    var erko = new Recipient();
    erko.setEmail("erko.risthein@tuleva.ee");

    message.setTo(List.of(funds, erko));

    var attachment = new MessageContent();
    attachment.setName(
        fundCode + " NAV arvutamine " + navDate.format(FILENAME_DATE_FORMAT) + ".csv");
    attachment.setType("text/csv");
    attachment.setContent(Base64.getEncoder().encodeToString(csvBytes));
    message.setAttachments(List.of(attachment));

    emailService.sendSystemEmail(message);

    log.info("NAV report email sent: fund={}, date={}, rows={}", fundCode, navDate, rows.size());
  }
}
