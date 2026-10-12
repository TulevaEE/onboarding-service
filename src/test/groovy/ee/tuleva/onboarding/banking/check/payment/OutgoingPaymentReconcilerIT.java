package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_NOT_EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.RETURN;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class OutgoingPaymentReconcilerIT {

  private static final Instant FRIDAY_15_00 = Instant.parse("2026-10-09T12:00:00Z");
  private static final Instant FRIDAY_16_30 = Instant.parse("2026-10-09T13:30:00Z");
  private static final Instant FRIDAY_17_30 = Instant.parse("2026-10-09T14:30:00Z");
  private static final Instant SUNDAY_04_02 = Instant.parse("2026-10-11T01:02:35Z");
  private static final Instant MONDAY_09_00 = Instant.parse("2026-10-12T06:00:00Z");
  private static final Instant MONDAY_17_00 = Instant.parse("2026-10-12T14:00:00Z");
  private static final Instant MONDAY_17_30 = Instant.parse("2026-10-12T14:30:00Z");
  private static final Instant TUESDAY_09_00 = Instant.parse("2026-10-13T06:00:00Z");
  private static final Instant TUESDAY_17_30 = Instant.parse("2026-10-13T14:30:00Z");
  private static final Instant WEDNESDAY_09_00 = Instant.parse("2026-10-14T06:00:00Z");

  private static final Instant DAY_BEFORE_CHRISTMAS_EVE_15_00 =
      Instant.parse("2026-12-23T13:00:00Z");
  private static final Instant DAY_BEFORE_CHRISTMAS_EVE_17_00 =
      Instant.parse("2026-12-23T15:00:00Z");
  private static final Instant DAY_BEFORE_CHRISTMAS_EVE_17_30 =
      Instant.parse("2026-12-23T15:30:00Z");
  private static final Instant CHRISTMAS_EVE_09_00 = Instant.parse("2026-12-24T07:00:00Z");
  private static final Instant MONDAY_AFTER_CHRISTMAS_09_00 = Instant.parse("2026-12-28T07:00:00Z");
  private static final Instant MONDAY_AFTER_CHRISTMAS_17_30 = Instant.parse("2026-12-28T15:30:00Z");
  private static final Instant TUESDAY_AFTER_CHRISTMAS_09_00 =
      Instant.parse("2026-12-29T07:00:00Z");

  @Autowired OutgoingPaymentRepository outgoingPaymentRepository;
  @Autowired PaymentCheckEventRepository paymentCheckEventRepository;

  @Test
  void aReturnSentOnSundayIsOverdueTheMorningAfterMondaysBrief() {
    var payment = submittedAt(SUNDAY_04_02);

    reconcileAt(MONDAY_09_00);
    reconcileAt(MONDAY_17_30);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(TUESDAY_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, TUESDAY_09_00));
  }

  @Test
  void aPaymentInFridaysBriefIsOverdueOnMondayMorning() {
    var payment = submittedAt(FRIDAY_15_00);

    reconcileAt(FRIDAY_17_30);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(MONDAY_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, MONDAY_09_00));
  }

  @Test
  void aPaymentSentAfterFridaysBriefBelongsToMondaysBrief() {
    var payment = submittedAt(FRIDAY_16_30);

    reconcileAt(MONDAY_09_00);
    reconcileAt(MONDAY_17_30);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(TUESDAY_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, TUESDAY_09_00));
  }

  @Test
  void aPaymentSentAfterTheBriefIsNotOverdueWhileTheNextDaysBriefIsStillBeingApproved() {
    var payment = submittedAt(MONDAY_17_00);

    reconcileAt(TUESDAY_17_30);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(WEDNESDAY_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, WEDNESDAY_09_00));
  }

  @Test
  void aPaymentInTheBriefBeforeAHolidayIsOverdueOnTheNextWorkingMorning() {
    var payment = submittedAt(DAY_BEFORE_CHRISTMAS_EVE_15_00);

    reconcileAt(DAY_BEFORE_CHRISTMAS_EVE_17_30);
    reconcileAt(CHRISTMAS_EVE_09_00);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(MONDAY_AFTER_CHRISTMAS_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, MONDAY_AFTER_CHRISTMAS_09_00));
  }

  @Test
  void publicHolidaysAreNotApprovalDays() {
    var payment = submittedAt(DAY_BEFORE_CHRISTMAS_EVE_17_00);

    reconcileAt(CHRISTMAS_EVE_09_00);
    reconcileAt(MONDAY_AFTER_CHRISTMAS_09_00);
    reconcileAt(MONDAY_AFTER_CHRISTMAS_17_30);
    assertThat(paymentCheckEventRepository.findAll()).isEmpty();

    reconcileAt(TUESDAY_AFTER_CHRISTMAS_09_00);
    assertThat(paymentCheckEventRepository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(notExecuted(payment, TUESDAY_AFTER_CHRISTMAS_09_00));
  }

  private void reconcileAt(Instant now) {
    var clock = Clock.fixed(now, UTC);
    var paymentCheckService =
        new PaymentCheckService(
            paymentCheckEventRepository,
            mock(OperationsNotificationService.class),
            event -> {},
            clock);
    new OutgoingPaymentReconciler(
            outgoingPaymentRepository,
            paymentCheckService,
            new PublicHolidays(),
            clock,
            Duration.ofMinutes(15))
        .reconcile();
  }

  private static PaymentCheckEvent notExecuted(OutgoingPayment payment, Instant foundAt) {
    return PaymentCheckEvent.builder()
        .checkType(PAYMENT_NOT_EXECUTED)
        .severity(WARNING)
        .externalKey(payment.getEndToEndId())
        .detail(
            "submitted %s and still not executed, type=RETURN".formatted(payment.getAttemptedAt()))
        .createdAt(foundAt)
        .lastSeenAt(foundAt)
        .build();
  }

  private OutgoingPayment submittedAt(Instant attemptedAt) {
    return outgoingPaymentRepository.save(
        OutgoingPayment.builder()
            .endToEndId("E2E" + attemptedAt.toEpochMilli())
            .paymentType(RETURN)
            .remitterIban("EE111111111111111111")
            .beneficiaryIban("EE222222222222222222")
            .amount(new BigDecimal("100.00"))
            .currency("EUR")
            .bodyHash("hash")
            .status(SUBMITTED)
            .attemptedAt(attemptedAt)
            .build());
  }
}
