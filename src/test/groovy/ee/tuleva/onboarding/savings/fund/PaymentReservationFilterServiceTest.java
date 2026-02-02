package ee.tuleva.onboarding.savings.fund;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Disabled
class PaymentReservationFilterServiceTest {

  private PublicHolidays publicHolidays;

  @BeforeEach
  void setUp() {
    publicHolidays = new PublicHolidays();
  }

  @Test
  @DisplayName("filters using previous working day cutoff when before 16:00 on working day")
  void filtersUsingPreviousWorkingDayCutoffBeforeSixteen() {
    // Monday 6th January 2025, 10:00 EET (08:00 UTC) - before 16:00
    var mondayMorning = Instant.parse("2025-01-06T08:00:00Z");
    var clock = Clock.fixed(mondayMorning, UTC);
    var service = new PaymentReservationFilterService(clock, publicHolidays);

    // Cutoff should be Friday 3rd January, 16:00 EET = 14:00 UTC
    var beforeCutoff = Instant.parse("2025-01-03T13:00:00Z"); // Friday 15:00 EET
    var afterCutoff = Instant.parse("2025-01-03T15:00:00Z"); // Friday 17:00 EET

    var paymentBeforeCutoff =
        createReservableSavingFundPayment().receivedBefore(beforeCutoff).build();
    var paymentAfterCutoff =
        createReservableSavingFundPayment().receivedBefore(afterCutoff).build();

    var result = service.filterPaymentsToReserve(List.of(paymentBeforeCutoff, paymentAfterCutoff));

    assertThat(result).containsExactly(paymentBeforeCutoff);
  }

  @Test
  @DisplayName("filters using today's cutoff when on working day after 16:00")
  void filtersUsingTodaysCutoffAfterSixteen() {
    // Monday 6th January 2025, 18:00 EET (16:00 UTC) - after 16:00
    var mondayEvening = Instant.parse("2025-01-06T16:00:00Z");
    var clock = Clock.fixed(mondayEvening, UTC);
    var service = new PaymentReservationFilterService(clock, publicHolidays);

    // Cutoff should be Monday 16:00 EET = 14:00 UTC
    var beforeCutoff = Instant.parse("2025-01-06T13:00:00Z"); // Monday 15:00 EET
    var afterCutoff = Instant.parse("2025-01-06T15:00:00Z"); // Monday 17:00 EET

    var paymentBeforeCutoff =
        createReservableSavingFundPayment().receivedBefore(beforeCutoff).build();
    var paymentAfterCutoff =
        createReservableSavingFundPayment().receivedBefore(afterCutoff).build();

    var result = service.filterPaymentsToReserve(List.of(paymentBeforeCutoff, paymentAfterCutoff));

    assertThat(result).containsExactly(paymentBeforeCutoff);
  }

  @Test
  @DisplayName("filters using previous working day cutoff when on weekend")
  void filtersUsingPreviousWorkingDayCutoffOnWeekend() {
    // Saturday 4th January 2025, 10:00 EET (08:00 UTC)
    var saturday = Instant.parse("2025-01-04T08:00:00Z");
    var clock = Clock.fixed(saturday, UTC);
    var service = new PaymentReservationFilterService(clock, publicHolidays);

    // Cutoff should be Friday 3rd January, 16:00 EET = 14:00 UTC
    var beforeCutoff = Instant.parse("2025-01-03T13:00:00Z"); // Friday 15:00 EET
    var afterCutoff = Instant.parse("2025-01-03T15:00:00Z"); // Friday 17:00 EET

    var paymentBeforeCutoff =
        createReservableSavingFundPayment().receivedBefore(beforeCutoff).build();
    var paymentAfterCutoff =
        createReservableSavingFundPayment().receivedBefore(afterCutoff).build();

    var result = service.filterPaymentsToReserve(List.of(paymentBeforeCutoff, paymentAfterCutoff));

    assertThat(result).containsExactly(paymentBeforeCutoff);
  }

  @Test
  @DisplayName("filters using previous working day cutoff when on public holidays")
  void filtersUsingPreviousWorkingDayCutoffOnPublicHoliday() {
    // Friday 27th December 2024 (after 3 public holidays: 24, 25, 26 Dec)
    var fridayAfterChristmas = Instant.parse("2024-12-27T08:00:00Z");
    var clock = Clock.fixed(fridayAfterChristmas, UTC);
    var service = new PaymentReservationFilterService(clock, publicHolidays);

    // Cutoff should be Monday 23rd December (before Christmas), 16:00 EET = 14:00 UTC
    var beforeCutoff = Instant.parse("2024-12-23T13:00:00Z"); // Monday 15:00 EET
    var afterCutoff = Instant.parse("2024-12-23T15:00:00Z"); // Monday 17:00 EET

    var paymentBeforeCutoff =
        createReservableSavingFundPayment().receivedBefore(beforeCutoff).build();
    var paymentAfterCutoff =
        createReservableSavingFundPayment().receivedBefore(afterCutoff).build();

    var result = service.filterPaymentsToReserve(List.of(paymentBeforeCutoff, paymentAfterCutoff));

    assertThat(result).containsExactly(paymentBeforeCutoff);
  }

  @Test
  @DisplayName("filters payments correctly with various scenarios")
  void filtersPaymentsWithVariousScenarios() {
    // Monday 6th January 2025, 18:00 EET (16:00 UTC) - after 16:00
    var mondayEvening = Instant.parse("2025-01-06T16:00:00Z");
    var clock = Clock.fixed(mondayEvening, UTC);
    var service = new PaymentReservationFilterService(clock, publicHolidays);

    // Cutoff is Monday 16:00 EET (14:00 UTC)
    var beforeCutoff = Instant.parse("2025-01-06T13:00:00Z"); // Monday 15:00 EET
    var afterCutoff = Instant.parse("2025-01-06T15:00:00Z"); // Monday 17:00 EET
    var fridayBeforeCutoff = Instant.parse("2025-01-03T13:00:00Z"); // Friday 15:00 EET, old one

    var paymentBeforeCutoff =
        createReservableSavingFundPayment().receivedBefore(beforeCutoff).build();
    var paymentAfterCutoff =
        createReservableSavingFundPayment().receivedBefore(afterCutoff).build();
    var paymentWithoutReceivedBefore =
        createReservableSavingFundPayment().receivedBefore(null).build();
    var cancelledPayment =
        createReservableSavingFundPayment()
            .receivedBefore(beforeCutoff)
            .cancelledAt(Instant.now())
            .build();
    var paymentFromFriday =
        createReservableSavingFundPayment().receivedBefore(fridayBeforeCutoff).build();

    var result =
        service.filterPaymentsToReserve(
            List.of(
                paymentBeforeCutoff,
                paymentAfterCutoff,
                paymentWithoutReceivedBefore,
                cancelledPayment,
                paymentFromFriday));

    assertThat(result).containsExactlyInAnyOrder(paymentBeforeCutoff, paymentFromFriday);
  }

  private SavingFundPayment.SavingFundPaymentBuilder createReservableSavingFundPayment() {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .userId(123L)
        .amount(new BigDecimal("500.00"))
        .currency(Currency.EUR)
        .description("Payment")
        .remitterIban("EE123456789")
        .remitterName("John Doe")
        .beneficiaryIban("EE987654321")
        .beneficiaryName("Tuleva")
        .createdAt(Instant.now());
  }
}
