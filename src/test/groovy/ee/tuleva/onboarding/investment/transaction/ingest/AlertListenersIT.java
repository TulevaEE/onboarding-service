package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AlertListenersIT {

  @Autowired private ApplicationEventPublisher eventPublisher;
  @MockitoBean private EmailService emailService;

  @BeforeEach
  void resetMock() {
    org.mockito.Mockito.reset(emailService);
    org.mockito.BDDMockito.given(
            emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);
  }

  @Test
  void executionMismatchEvent_triggersOneOutboundEmail() {
    eventPublisher.publishEvent(
        new ExecutionMismatchEvent(
            42L,
            "IE000F60HVH9",
            new BigDecimal("4.7255"),
            new BigDecimal("4.7800"),
            new BigDecimal("1.15"),
            LocalDate.of(2026, 5, 11)));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getSubject())
        .isEqualTo("[HOIATUS] SEB hind erineb NAV-hinnast – 2026-05-11");
  }

  @Test
  void navMissingEvent_triggersOneOutboundEmail() {
    eventPublisher.publishEvent(
        new NavMissingEvent(42L, "IE000F60HVH9", LocalDate.of(2026, 5, 11)));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getSubject()).isEqualTo("[INFO] NAV andmed puuduvad – 2026-05-11");
  }

  @Test
  void unrelatedEvent_doesNothing() {
    eventPublisher.publishEvent(new Object());

    verifyNoInteractions(emailService);
  }
}
