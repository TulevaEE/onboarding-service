package ee.tuleva.onboarding.savings.fund.report;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("production")
class TrusteeReportJob {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final TrusteeReportRepository repository;
  private final TrusteeReportCsvGenerator csvGenerator;
  private final EmailService emailService;
  private final PublicHolidays publicHolidays;

  @Scheduled(cron = "0 15 16 * * *", zone = TIMEZONE)
  @SchedulerLock(name = "TrusteeReportJob", lockAtMostFor = "23h", lockAtLeastFor = "30m")
  public void sendReport() {
    var today = LocalDate.now(clock());

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping trustee report on non-working day: date={}", today);
      return;
    }

    log.info("Generating trustee report: date={}", today);

    var rows = repository.findAll();
    var csvBytes = csvGenerator.generate(rows);
    var message = buildMessage(today, csvBytes);

    emailService.sendSystemEmail(message);
    log.info("Trustee report sent: date={}, rows={}", today, rows.size());
  }

  private MandrillMessage buildMessage(LocalDate reportDate, byte[] csvBytes) {
    var message = new MandrillMessage();
    message.setFromEmail("funds@tuleva.ee");
    message.setFromName("Tuleva");
    message.setSubject("TKF100 osakute registri väljavõte " + reportDate.format(DATE_FORMAT));
    message.setHtml(
        """
        <p>Tere,</p>
        <p>Manuses on Tuleva Täiendava Kogumisfondi (TKF100) osakute registri väljavõte.</p>
        <p>See on automaatne kiri.</p>
        <p>Lugupidamisega,<br>Tuleva robot</p>
        """);
    message.setTo(buildRecipients());
    message.setAttachments(List.of(buildAttachment(reportDate, csvBytes)));
    return message;
  }

  private List<Recipient> buildRecipients() {
    var to = new Recipient();
    to.setEmail("trustee@seb.ee");
    to.setType(TO);

    var ccFunds = new Recipient();
    ccFunds.setEmail("funds@tuleva.ee");
    ccFunds.setType(CC);

    var ccCompliance = new Recipient();
    ccCompliance.setEmail("compliance@tuleva.ee");
    ccCompliance.setType(CC);

    var ccErko = new Recipient();
    ccErko.setEmail("erko.risthein@tuleva.ee");
    ccErko.setType(CC);

    return List.of(to, ccFunds, ccCompliance, ccErko);
  }

  private MessageContent buildAttachment(LocalDate reportDate, byte[] csvBytes) {
    var attachment = new MessageContent();
    attachment.setName("TKF100_osakute_registri_valjavote_" + reportDate + ".csv");
    attachment.setType("text/csv");
    attachment.setContent(Base64.getEncoder().encodeToString(csvBytes));
    return attachment;
  }
}
