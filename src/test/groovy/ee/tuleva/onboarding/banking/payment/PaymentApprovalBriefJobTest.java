package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.time.ZoneOffset.UTC;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalBriefJobTest {
  private static final LocalDate WEDNESDAY = LocalDate.parse("2026-09-23");
  private static final Instant WEDNESDAY_AT_16_10_IN_TALLINN =
      Instant.parse("2026-09-23T13:10:00Z");

  @Mock private PaymentApprovalBriefService briefService;
  @Mock private PaymentCheckService paymentCheckService;
  @Mock private OperationsNotificationService notificationService;

  private final PaymentApprovalBriefFormatter formatter = new PaymentApprovalBriefFormatter();
  private PaymentApprovalBriefJob job;

  @BeforeEach
  void setUp() {
    job =
        new PaymentApprovalBriefJob(
            briefService,
            formatter,
            paymentCheckService,
            notificationService,
            new PublicHolidays(),
            Clock.fixed(WEDNESDAY_AT_16_10_IN_TALLINN, UTC));
  }

  @Test
  void postBrief_postsToTheSavingsChannel() {
    var brief = new PaymentApprovalBrief(WEDNESDAY, List.of(), List.of(), 0, List.of(), false);
    given(briefService.build(WEDNESDAY, List.of())).willReturn(brief);

    job.postBrief();

    verify(notificationService).sendMessage(formatter.format(brief), SAVINGS);
  }
}
