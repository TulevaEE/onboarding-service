package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static java.time.DayOfWeek.*;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.mockito.Mockito.*;
import static shadow.org.assertj.core.api.AssertionsForClassTypes.assertThat;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuingJobTest {

  private IssuerService issuerService;
  private SavingFundPaymentRepository paymentRepository;
  private SavingsFundNavProvider navProvider;

  @BeforeEach
  void setUp() {
    paymentRepository = mock(SavingFundPaymentRepository.class);
    issuerService = mock(IssuerService.class);
    navProvider = mock(SavingsFundNavProvider.class);
    when(navProvider.getCurrentNav()).thenReturn(BigDecimal.ONE);
  }

  boolean isReservedDateWorkingDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getReceivedBefore(), ZoneId.of("Europe/Tallinn"));
    return new PublicHolidays().isWorkingDay(paymentReservedDate);
  }

  DayOfWeek getDayOfWeekOfReservedDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getReceivedBefore(), ZoneId.of("Europe/Tallinn"));
    return paymentReservedDate.getDayOfWeek();
  }

  @Test
  @DisplayName("processes RESERVED payments from yesterday before cutoff, ignores those from today")
  void processMessages() {
    var now = Instant.parse("2025-01-03T14:00:00Z"); // friday 3rd january 2025
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(2, DAYS).minus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentToday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(2, HOURS))
            .status(RESERVED)
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentToday, reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentToday, nav);
  }

  @Test
  @DisplayName("when running before 16:00, only includes payments from two working days before")
  void before16TwoWorkingDaysBefore() {
    var now = Instant.parse("2025-01-10T12:00:00Z"); // friday 3rd january 2025 before 16:00 cutoff
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromTwoDaysBefore =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(2, DAYS).minus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(2, DAYS).plus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(1, DAYS))
            .status(RESERVED)
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(
            List.of(
                reservedPaymentFromTwoDaysBefore,
                reservedPaymentFromYesterday,
                reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromTwoDaysBefore, nav);
    verify(issuerService, never()).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never())
        .processPayment(reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff, nav);
  }

  @Test
  @DisplayName("when running on weekend, only includes payments from two working days before")
  void runOnWeekend() {
    var now = Instant.parse("2025-01-12T14:00:00Z"); // sunday
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromTwoDaysBefore =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(4, DAYS).minus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(3, DAYS).plus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(1, DAYS))
            .status(RESERVED)
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(
            List.of(
                reservedPaymentFromTwoDaysBefore,
                reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff,
                reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromTwoDaysBefore, nav);
    verify(issuerService, never()).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never())
        .processPayment(reservedPaymentFromTwoDaysBeforeButMadeAfterCutoff, nav);
  }

  @Test
  @DisplayName(
      "processes RESERVED payments from yesterday before cutoff, ignores those from yesterday made after cutoff")
  void processMessagesPostCutoff() {
    var now = Instant.parse("2025-01-03T14:00:00Z"); // friday 3rd january 2025
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(1, DAYS).minus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentAfterCutoff =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(1, DAYS).plus(2, HOURS))
            .status(RESERVED)
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentAfterCutoff, reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentAfterCutoff, nav);
  }

  @Test
  @DisplayName("when running on monday, does not process reserved payment from weekend")
  void doesNotProcessWeekendReserved() {
    var now = Instant.parse("2025-01-06T14:00:00Z"); // monday 5th january 2025
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromWeekend =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(1, DAYS).minus(4, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentMadeOnFriday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(3, DAYS).minus(2, HOURS))
            .status(RESERVED)
            .build();

    assertThat(getDayOfWeekOfReservedDay(reservedPaymentMadeOnFriday)).isEqualTo(FRIDAY);
    assertThat(getDayOfWeekOfReservedDay(reservedPaymentFromWeekend)).isEqualTo(SUNDAY);

    assertThat(isReservedDateWorkingDay(reservedPaymentFromWeekend)).isFalse();
    assertThat(isReservedDateWorkingDay(reservedPaymentMadeOnFriday)).isTrue();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentMadeOnFriday, reservedPaymentFromWeekend));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentMadeOnFriday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentFromWeekend, nav);
  }

  @Test
  @DisplayName(
      "when running on day after holidays, process payment from before holidays but does not process reserved payment from public holiday")
  void doesNotProcessPublicHoliday() {
    var now =
        Instant.parse(
            "2024-12-27T14:00:00Z"); // friday 27th december 2024, with 3 public holidays before it
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository, navProvider);

    var nav = BigDecimal.ONE;

    var reservedPaymentFromMondayBeforeChristmas =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(4, DAYS).minus(2, HOURS))
            .status(RESERVED)
            .build();

    var reservedPaymentMadeOnPublicHoliday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .receivedBefore(clock.instant().minus(2, DAYS).minus(5, HOURS))
            .status(RESERVED)
            .build();

    assertThat(getDayOfWeekOfReservedDay(reservedPaymentFromMondayBeforeChristmas))
        .isEqualTo(MONDAY);
    assertThat(getDayOfWeekOfReservedDay(reservedPaymentMadeOnPublicHoliday)).isEqualTo(WEDNESDAY);

    assertThat(isReservedDateWorkingDay(reservedPaymentFromMondayBeforeChristmas)).isTrue();
    assertThat(isReservedDateWorkingDay(reservedPaymentMadeOnPublicHoliday)).isFalse();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(
            List.of(reservedPaymentFromMondayBeforeChristmas, reservedPaymentMadeOnPublicHoliday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromMondayBeforeChristmas, nav);
    verify(issuerService, never()).processPayment(reservedPaymentMadeOnPublicHoliday, nav);
  }
}
