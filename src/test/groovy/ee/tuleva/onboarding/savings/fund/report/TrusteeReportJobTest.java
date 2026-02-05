package ee.tuleva.onboarding.savings.fund.report;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrusteeReportJobTest {

  @Mock private TrusteeReportRepository repository;
  @Mock private TrusteeReportCsvGenerator csvGenerator;
  @Mock private EmailService emailService;
  @InjectMocks private TrusteeReportJob job;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @Test
  void sendsReportEmailWithCsvAttachment() {
    var rows =
        List.of(
            TrusteeReportRow.builder()
                .reportDate(LocalDate.of(2020, 1, 1))
                .nav(ONE)
                .issuedUnits(ZERO)
                .issuedAmount(ZERO)
                .redeemedUnits(ZERO)
                .redeemedAmount(ZERO)
                .totalOutstandingUnits(ZERO)
                .build());
    var csvBytes = "csv-content".getBytes(UTF_8);

    when(repository.findAll()).thenReturn(rows);
    when(csvGenerator.generate(rows)).thenReturn(csvBytes);

    job.sendReport();

    var messageCaptor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(messageCaptor.capture());

    var message = messageCaptor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject()).isEqualTo("TKF100 osakute registri väljavõte 01.01.2020");

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
    assertThat(attachment.getName()).isEqualTo("TKF100_osakute_registri_valjavote_2020-01-01.csv");
    assertThat(attachment.getType()).isEqualTo("text/csv");
    assertThat(Base64.getDecoder().decode(attachment.getContent())).isEqualTo(csvBytes);
  }
}
