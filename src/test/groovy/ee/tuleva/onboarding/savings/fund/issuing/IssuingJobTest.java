package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.time.DayOfWeek.*;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import ee.tuleva.onboarding.savings.fund.notification.IssuingCompletedEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IssuingJobTest {

  private IssuerService issuerService;
  private SavingFundPaymentRepository paymentRepository;
  private FundNavProvider navProvider;
  private ApplicationEventPublisher eventPublisher;
  private static final BigDecimal nav = ONE;

  @BeforeEach
  void setUp() {
    paymentRepository = mock(SavingFundPaymentRepository.class);
    issuerService = mock(IssuerService.class);
    navProvider = mock(FundNavProvider.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    lenient().when(navProvider.getVerifiedNavForIssuingAndRedeeming(any())).thenReturn(nav);
    lenient()
        .when(issuerService.processPayment(any(), any()))
        .thenReturn(new IssuingResult(ZERO, ZERO));
  }

  private IssuingJob createIssuingJob(Instant now) {
    var clock = Clock.fixed(now, UTC);
    return new IssuingJob(clock, issuerService, paymentRepository, navProvider, eventPublisher);
  }

  private boolean isReservedDateWorkingDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getReceivedBefore(), ZoneId.of("Europe/Tallinn"));
    return new PublicHolidays().isWorkingDay(paymentReservedDate);
  }

  private DayOfWeek getDayOfWeekOfReservedDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getReceivedBefore(), ZoneId.of("Europe/Tallinn"));
    return paymentReservedDate.getDayOfWeek();
  }

  @Test
  void processesPaymentsFromYesterdayBeforeCutoff_ignoresFromToday() {
    var now = Instant.parse("2025-01-03T14:00:00Z");
    var issuingJob = createIssuingJob(now);

    var reservedPaymentFromYesterday =
        aPayment().receivedBefore(now.minus(2, DAYS).minus(4, HOURS)).status(RESERVED).build();

    var reservedPaymentToday =
        aPayment().receivedBefore(now.minus(2, HOURS)).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentToday, reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentToday, nav);
  }

  @Test
  void before16_onlyIncludesPaymentsFromTwoWorkingDaysBefore() {
    var now = Instant.parse("2025-01-10T12:00:00Z");
    var issuingJob = createIssuingJob(now);

    var paymentFromTwoDaysBefore =
        aPayment().receivedBefore(now.minus(2, DAYS).minus(4, HOURS)).status(RESERVED).build();

    var paymentFromTwoDaysBeforeButAfterCutoff =
        aPayment().receivedBefore(now.minus(2, DAYS).plus(4, HOURS)).status(RESERVED).build();

    var paymentFromYesterday =
        aPayment().receivedBefore(now.minus(1, DAYS)).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(
            List.of(
                paymentFromTwoDaysBefore,
                paymentFromYesterday,
                paymentFromTwoDaysBeforeButAfterCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentFromTwoDaysBefore, nav);
    verify(issuerService, never()).processPayment(paymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(paymentFromTwoDaysBeforeButAfterCutoff, nav);
  }

  @Test
  void onWeekend_onlyIncludesPaymentsFromTwoWorkingDaysBefore() {
    var now = Instant.parse("2025-01-12T14:00:00Z");
    var issuingJob = createIssuingJob(now);

    var paymentFromTwoDaysBefore =
        aPayment().receivedBefore(now.minus(4, DAYS).minus(4, HOURS)).status(RESERVED).build();

    var paymentFromTwoDaysBeforeButAfterCutoff =
        aPayment().receivedBefore(now.minus(3, DAYS).plus(4, HOURS)).status(RESERVED).build();

    var paymentFromYesterday =
        aPayment().receivedBefore(now.minus(1, DAYS)).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(
            List.of(
                paymentFromTwoDaysBefore,
                paymentFromTwoDaysBeforeButAfterCutoff,
                paymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentFromTwoDaysBefore, nav);
    verify(issuerService, never()).processPayment(paymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(paymentFromTwoDaysBeforeButAfterCutoff, nav);
  }

  @Test
  void processesPaymentsBeforeCutoff_ignoresAfterCutoff() {
    var now = Instant.parse("2025-01-03T14:00:00Z");
    var issuingJob = createIssuingJob(now);

    var paymentFromYesterday =
        aPayment().receivedBefore(now.minus(1, DAYS).minus(4, HOURS)).status(RESERVED).build();

    var paymentAfterCutoff =
        aPayment().receivedBefore(now.minus(1, DAYS).plus(2, HOURS)).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentAfterCutoff, paymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(paymentAfterCutoff, nav);
  }

  @Test
  void onMonday_doesNotProcessWeekendPayments() {
    var now = Instant.parse("2025-01-06T14:00:00Z");
    var issuingJob = createIssuingJob(now);

    var paymentFromWeekend =
        aPayment().receivedBefore(now.minus(1, DAYS).minus(4, HOURS)).status(RESERVED).build();

    var paymentMadeOnFriday =
        aPayment().receivedBefore(now.minus(3, DAYS).minus(2, HOURS)).status(RESERVED).build();

    assertThat(getDayOfWeekOfReservedDay(paymentMadeOnFriday)).isEqualTo(FRIDAY);
    assertThat(getDayOfWeekOfReservedDay(paymentFromWeekend)).isEqualTo(SUNDAY);

    assertThat(isReservedDateWorkingDay(paymentFromWeekend)).isFalse();
    assertThat(isReservedDateWorkingDay(paymentMadeOnFriday)).isTrue();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentMadeOnFriday, paymentFromWeekend));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentMadeOnFriday, nav);
    verify(issuerService, never()).processPayment(paymentFromWeekend, nav);
  }

  @Test
  void afterHolidays_processesBeforeHolidays_ignoresPublicHoliday() {
    var now = Instant.parse("2024-12-27T14:00:00Z");
    var issuingJob = createIssuingJob(now);

    var paymentFromMondayBeforeChristmas =
        aPayment().receivedBefore(now.minus(4, DAYS).minus(2, HOURS)).status(RESERVED).build();

    var paymentMadeOnPublicHoliday =
        aPayment().receivedBefore(now.minus(2, DAYS).minus(5, HOURS)).status(RESERVED).build();

    assertThat(getDayOfWeekOfReservedDay(paymentFromMondayBeforeChristmas)).isEqualTo(MONDAY);
    assertThat(getDayOfWeekOfReservedDay(paymentMadeOnPublicHoliday)).isEqualTo(WEDNESDAY);

    assertThat(isReservedDateWorkingDay(paymentFromMondayBeforeChristmas)).isTrue();
    assertThat(isReservedDateWorkingDay(paymentMadeOnPublicHoliday)).isFalse();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentFromMondayBeforeChristmas, paymentMadeOnPublicHoliday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentFromMondayBeforeChristmas, nav);
    verify(issuerService, never()).processPayment(paymentMadeOnPublicHoliday, nav);
  }

  @Test
  void atMidnightTallinnOnMonday_usesBeforeCutoffBranch() {
    var issuingJob = createIssuingJob(Instant.parse("2025-01-12T22:00:00Z"));

    var paymentBeforeThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-09T13:00:00Z")).status(RESERVED).build();

    var paymentAfterThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-09T15:00:00Z")).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentBeforeThursdayCutoff, paymentAfterThursdayCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentBeforeThursdayCutoff, nav);
    verify(issuerService, never()).processPayment(paymentAfterThursdayCutoff, nav);
  }

  @Test
  void atMidnightTallinnOnMondayDuringSummerDst_usesBeforeCutoffBranch() {
    var issuingJob = createIssuingJob(Instant.parse("2025-07-13T21:00:00Z"));

    var paymentBeforeThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-07-10T12:00:00Z")).status(RESERVED).build();

    var paymentAfterThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-07-10T14:00:00Z")).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentBeforeThursdayCutoff, paymentAfterThursdayCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentBeforeThursdayCutoff, nav);
    verify(issuerService, never()).processPayment(paymentAfterThursdayCutoff, nav);
  }

  @Test
  void fridayNightInTallinn_usesSameCutoffAsFridayAfterCutoff() {
    var issuingJob = createIssuingJob(Instant.parse("2025-01-10T22:00:00Z"));

    var paymentBeforeThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-09T13:00:00Z")).status(RESERVED).build();

    var paymentAfterThursdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-09T15:00:00Z")).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentBeforeThursdayCutoff, paymentAfterThursdayCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentBeforeThursdayCutoff, nav);
    verify(issuerService, never()).processPayment(paymentAfterThursdayCutoff, nav);
  }

  @Test
  void midweekMidnightTallinn_usesBeforeCutoffBranch() {
    var issuingJob = createIssuingJob(Instant.parse("2025-01-08T22:00:00Z"));

    var paymentBeforeTuesdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-07T13:00:00Z")).status(RESERVED).build();

    var paymentAfterTuesdayCutoff =
        aPayment().receivedBefore(Instant.parse("2025-01-07T15:00:00Z")).status(RESERVED).build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(paymentBeforeTuesdayCutoff, paymentAfterTuesdayCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(paymentBeforeTuesdayCutoff, nav);
    verify(issuerService, never()).processPayment(paymentAfterTuesdayCutoff, nav);
  }

  @Test
  void skipsNavFetchWhenNoPaymentsToProcess() {
    var issuingJob = createIssuingJob(Instant.parse("2025-01-03T14:00:00Z"));

    when(paymentRepository.findPaymentsWithStatus(RESERVED)).thenReturn(List.of());

    issuingJob.runJob();

    verify(navProvider, never()).getVerifiedNavForIssuingAndRedeeming(any());
    verifyNoInteractions(issuerService);
  }

  @Test
  void publishesIssuingCompletedEvent() {
    var issuingJob = createIssuingJob(Instant.parse("2025-01-03T14:00:00Z"));

    var nav = new BigDecimal("9.9918");
    when(navProvider.getVerifiedNavForIssuingAndRedeeming(any())).thenReturn(nav);

    var payment1 =
        aPayment()
            .receivedBefore(Instant.parse("2025-01-02T10:00:00Z"))
            .amount(new BigDecimal("500.00"))
            .status(RESERVED)
            .build();
    var payment2 =
        aPayment()
            .receivedBefore(Instant.parse("2025-01-02T11:00:00Z"))
            .amount(new BigDecimal("1000.00"))
            .status(RESERVED)
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(payment1, payment2));
    when(issuerService.processPayment(payment1, nav))
        .thenReturn(new IssuingResult(new BigDecimal("500.00"), new BigDecimal("50.04100")));
    when(issuerService.processPayment(payment2, nav))
        .thenReturn(new IssuingResult(new BigDecimal("1000.00"), new BigDecimal("100.08201")));

    issuingJob.runJob();

    verify(eventPublisher)
        .publishEvent(
            new IssuingCompletedEvent(
                2, new BigDecimal("1500.00"), new BigDecimal("150.12301"), nav));
  }
}
