package ee.tuleva.onboarding.savings.fund.report;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.savings.fund.notification.TrusteeReportSentEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TrusteeReportJobTest {

  @Mock private TrusteeReportRepository repository;
  @Mock private TrusteeReportCsvGenerator csvGenerator;
  @Mock private EmailService emailService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final PublicHolidays publicHolidays = new PublicHolidays();

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  private TrusteeReportJob createJob() {
    return new TrusteeReportJob(
        repository, csvGenerator, emailService, publicHolidays, eventPublisher);
  }

  private void setClockTo(LocalDate date) {
    var instant = LocalDateTime.of(date, java.time.LocalTime.of(16, 15)).toInstant(UTC);
    ClockHolder.setClock(Clock.fixed(instant, UTC));
  }

  @Test
  void sendsReportOnWorkingDay() {
    var workingDay = LocalDate.of(2020, 1, 2); // Thursday
    setClockTo(workingDay);

    var todayRow =
        TrusteeReportRow.builder()
            .reportDate(workingDay)
            .nav(ONE)
            .issuedUnits(ZERO)
            .issuedAmount(ZERO)
            .redeemedUnits(ZERO)
            .redeemedAmount(ZERO)
            .totalOutstandingUnits(ZERO)
            .build();
    var olderRow =
        TrusteeReportRow.builder()
            .reportDate(workingDay.minusDays(1))
            .nav(new BigDecimal("999"))
            .issuedUnits(new BigDecimal("999"))
            .issuedAmount(new BigDecimal("999"))
            .redeemedUnits(new BigDecimal("999"))
            .redeemedAmount(new BigDecimal("999"))
            .totalOutstandingUnits(new BigDecimal("999"))
            .build();
    var rows = List.of(todayRow, olderRow);
    var csvBytes = "csv-content".getBytes(UTF_8);

    when(repository.findAll()).thenReturn(rows);
    when(csvGenerator.generate(rows)).thenReturn(csvBytes);

    createJob().sendReport();

    var messageCaptor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(messageCaptor.capture());

    var message = messageCaptor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject()).isEqualTo("TKF100 osakute registri väljavõte 02.01.2020");

    var recipients = message.getTo();
    assertThat(recipients).hasSize(4);
    assertThat(recipients.get(0).getEmail()).isEqualTo("trustee@seb.ee");
    assertThat(recipients.get(0).getType()).isEqualTo(TO);
    assertThat(recipients.get(1).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(recipients.get(1).getType()).isEqualTo(CC);
    assertThat(recipients.get(2).getEmail()).isEqualTo("compliance@tuleva.ee");
    assertThat(recipients.get(2).getType()).isEqualTo(CC);
    assertThat(recipients.get(3).getEmail()).isEqualTo("erko.risthein@tuleva.ee");
    assertThat(recipients.get(3).getType()).isEqualTo(CC);

    assertThat(message.getHtml()).contains("Tuleva Täiendava Kogumisfondi");
    assertThat(message.getHtml()).contains("automaatne kiri");

    var attachment = message.getAttachments().getFirst();
    assertThat(attachment.getName()).isEqualTo("TKF100_osakute_registri_valjavote_2020-01-02.csv");
    assertThat(attachment.getType()).isEqualTo("text/csv");
    assertThat(Base64.getDecoder().decode(attachment.getContent())).isEqualTo(csvBytes);

    verify(eventPublisher)
        .publishEvent(
            new TrusteeReportSentEvent(
                workingDay, 2, new BigDecimal("1.0000"), ZERO, ZERO, ZERO, ZERO, ZERO));
  }

  @Test
  void skipsReportOnWeekend() {
    var saturday = LocalDate.of(2020, 1, 4); // Saturday
    setClockTo(saturday);

    createJob().sendReport();

    verifyNoInteractions(emailService, eventPublisher);
  }

  @Test
  void skipsReportWhenSaturdayInTallinnButFridayInUtc() {
    var fridayNightUtc = Instant.parse("2025-01-10T22:00:00Z"); // Sat 00:00 Tallinn
    ClockHolder.setClock(Clock.fixed(fridayNightUtc, UTC));

    createJob().sendReport();

    verifyNoInteractions(emailService, eventPublisher);
  }

  @Test
  void skipsReportOnPublicHoliday() {
    var newYearsDay = LocalDate.of(2020, 1, 1); // New Year's Day
    setClockTo(newYearsDay);

    createJob().sendReport();

    verifyNoInteractions(emailService, eventPublisher);
  }
}
