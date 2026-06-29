package ee.tuleva.onboarding.savings.fund.nav;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.BCC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavReportEmailSenderTest {

  @Mock private NavReportCsvGenerator navReportCsvGenerator;
  @Mock private EmailService emailService;

  @InjectMocks private NavReportEmailSender navReportEmailSender;

  @Test
  void sendsEmailWithCsvAttachment() {
    var navDate = LocalDate.of(2026, 3, 13);
    var result = buildResult(navDate);

    var rows = List.of(NavReportRow.builder().navDate(navDate).fundCode("TKF100").build());
    var csvBytes = "csv-content".getBytes(UTF_8);

    when(navReportCsvGenerator.generate(rows)).thenReturn(csvBytes);
    when(emailService.sendSystemEmail(any())).thenReturn(true);

    boolean sent = navReportEmailSender.send(rows, result);

    assertThat(sent).isTrue();

    var messageCaptor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(messageCaptor.capture());

    var message = messageCaptor.getValue();
    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject()).isEqualTo("TKF100 NAV arvutamine 13.03.2026");

    assertThat(message.getTo()).hasSize(5);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("trustee@seb.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(2).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(2).getType()).isEqualTo(CC);
    assertThat(message.getTo().get(3).getEmail()).isEqualTo("compliance@tuleva.ee");
    assertThat(message.getTo().get(3).getType()).isEqualTo(CC);
    assertThat(message.getTo().get(4).getEmail()).isEqualTo("erko.risthein@tuleva.ee");
    assertThat(message.getTo().get(4).getType()).isEqualTo(BCC);

    var attachment = message.getAttachments().getFirst();
    assertThat(attachment.getName()).isEqualTo("TKF100 NAV arvutamine 16032026.csv");
    assertThat(attachment.getType()).isEqualTo("text/csv");
    assertThat(Base64.getDecoder().decode(attachment.getContent())).isEqualTo(csvBytes);
  }

  @Test
  void returnsFalse_whenEmailFails() {
    var navDate = LocalDate.of(2026, 3, 13);
    var result = buildResult(navDate);

    var rows = List.of(NavReportRow.builder().navDate(navDate).fundCode("TKF100").build());
    var csvBytes = "csv-content".getBytes(UTF_8);

    when(navReportCsvGenerator.generate(rows)).thenReturn(csvBytes);
    when(emailService.sendSystemEmail(any())).thenReturn(false);

    boolean sent = navReportEmailSender.send(rows, result);

    assertThat(sent).isFalse();
  }

  @Test
  void sendForReview_sendsToInternalRecipientsOnly_excludingTrustee() {
    var navDate = LocalDate.of(2026, 3, 13);
    var result = buildResult(navDate);

    var rows = List.of(NavReportRow.builder().navDate(navDate).fundCode("TKF100").build());
    var csvBytes = "csv-content".getBytes(UTF_8);

    when(navReportCsvGenerator.generate(rows)).thenReturn(csvBytes);
    when(emailService.sendSystemEmail(any())).thenReturn(true);

    boolean sent = navReportEmailSender.sendForReview(rows, result);

    assertThat(sent).isTrue();

    var messageCaptor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(messageCaptor.capture());

    var message = messageCaptor.getValue();
    assertThat(message.getTo()).hasSize(4);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);
    assertThat(message.getTo().get(2).getEmail()).isEqualTo("compliance@tuleva.ee");
    assertThat(message.getTo().get(2).getType()).isEqualTo(CC);
    assertThat(message.getTo().get(3).getEmail()).isEqualTo("erko.risthein@tuleva.ee");
    assertThat(message.getTo().get(3).getType()).isEqualTo(BCC);
    assertThat(message.getTo())
        .noneMatch(recipient -> recipient.getEmail().equals("trustee@seb.ee"));
  }

  private NavCalculationResult buildResult(LocalDate navDate) {
    return NavCalculationResult.builder()
        .fund(TKF100)
        .calculationDate(LocalDate.of(2026, 3, 16))
        .positionReportDate(navDate)
        .priceDate(navDate)
        .calculatedAt(Instant.parse("2026-03-16T13:20:00Z"))
        .securitiesDetail(List.of())
        .cashPosition(ZERO)
        .receivables(ZERO)
        .payables(ZERO)
        .pendingSubscriptions(ZERO)
        .pendingRedemptions(ZERO)
        .managementFeeAccrual(ZERO)
        .depotFeeAccrual(ZERO)
        .blackrockAdjustment(ZERO)
        .unitsOutstanding(ZERO)
        .navPerUnit(BigDecimal.ONE)
        .aum(ZERO)
        .build();
  }
}
