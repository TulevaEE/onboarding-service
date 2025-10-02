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

  @BeforeEach
  void setUp() {
    paymentRepository = mock(SavingFundPaymentRepository.class);
    issuerService = mock(IssuerService.class);
  }

  boolean isReservedDateWorkingDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getStatusChangedAt(), ZoneId.of("Europe/Tallinn"));
    return new PublicHolidays().isWorkingDay(paymentReservedDate);
  }

  DayOfWeek getDayOfWeekOfReservedDay(SavingFundPayment payment) {
    var paymentReservedDate =
        LocalDate.ofInstant(payment.getStatusChangedAt(), ZoneId.of("Europe/Tallinn"));
    return paymentReservedDate.getDayOfWeek();
  }

  @Test
  @DisplayName("processes RESERVED payments from yesterday, ignores those from today")
  void processMessages() {
    var now = Instant.parse("2025-01-03T15:00:00Z"); // friday 3rd january 2025
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository);

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
            .createdAt(clock.instant().minus(2, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, DAYS))
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
            .createdAt(clock.instant().minus(2, HOURS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, HOURS))
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentToday, reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentToday, nav);
  }

  @Test
  @DisplayName("when running on monday, does not process reserved payment from weekend")
  void doesNotProcessWeekendReserved() {
    var now = Instant.parse("2025-01-06T15:00:00Z"); // monday 5th january 2025
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository);

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
            .createdAt(clock.instant().minus(1, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, DAYS))
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
            .createdAt(clock.instant().minus(3, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(3, DAYS))
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
            "2024-12-27T15:00:00Z"); // friday 27th december 2024, with 3 public holidays before it
    var clock = Clock.fixed(now, UTC);
    var issuingJob = new IssuingJob(clock, issuerService, paymentRepository);

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
            .createdAt(clock.instant().minus(4, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(4, DAYS))
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
            .createdAt(clock.instant().minus(2, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(2, DAYS))
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
