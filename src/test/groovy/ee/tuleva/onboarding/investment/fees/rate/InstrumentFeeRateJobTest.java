package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.NONE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Failed;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Resolved;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentFeeRateJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate THIRD_BUSINESS_DAY_OF_JUNE = LocalDate.of(2026, 6, 3);
  private static final YearMonth MARCH = YearMonth.of(2026, 3);
  private static final YearMonth APRIL = YearMonth.of(2026, 4);
  private static final YearMonth MAY = YearMonth.of(2026, 5);

  @Mock private InstrumentOcfService service;
  @Mock private InstrumentFeeRateNotifier notifier;

  @Test
  void onTheThirdBusinessDayEveryClosedMonthNotYetResolvedAfterItClosedIsResolved() {
    given(service.hasRatesResolvedAfterItClosed(any())).willReturn(false);
    given(service.hasRatesResolvedAfterItClosed(APRIL)).willReturn(true);

    jobOn(THIRD_BUSINESS_DAY_OF_JUNE).resolveTheClosedMonthsIfDue();

    verify(service).resolve(MARCH);
    verify(service, never()).resolve(APRIL);
    verify(service).resolve(MAY);
    verify(service, never()).resolve(YearMonth.of(2026, 6));
    verify(service, never()).resolve(YearMonth.of(2026, 2));
  }

  @Test
  void theResolvedMonthsAreAnnouncedSoAFallbackCanBeFixedBeforeTheConsumersRead() {
    given(service.hasRatesResolvedAfterItClosed(any())).willReturn(true);
    given(service.hasRatesResolvedAfterItClosed(MAY)).willReturn(false);
    var mayRates = List.of(rate(MAY));
    given(service.resolve(MAY)).willReturn(mayRates);

    jobOn(THIRD_BUSINESS_DAY_OF_JUNE).resolveTheClosedMonthsIfDue();

    verify(notifier).announce(List.of(new Resolved(MAY, mayRates)));
  }

  @Test
  void aMonthAlreadyResolvedAfterItClosedIsNotResolvedAgainAndNothingIsAnnounced() {
    given(service.hasRatesResolvedAfterItClosed(any())).willReturn(true);

    jobOn(THIRD_BUSINESS_DAY_OF_JUNE).resolveTheClosedMonthsIfDue();

    verify(service, never()).resolve(any());
    verifyNoInteractions(notifier);
  }

  @Test
  void beforeTheThirdBusinessDayNothingIsResolved() {
    jobOn(LocalDate.of(2026, 6, 2)).resolveTheClosedMonthsIfDue();

    verify(service, never()).resolve(any());
    verifyNoInteractions(notifier);
  }

  @Test
  void aLaterRunInTheWindowResolvesTheMonthsStillNotResolvedAfterTheyClosed() {
    given(service.hasRatesResolvedAfterItClosed(any())).willReturn(true);
    given(service.hasRatesResolvedAfterItClosed(MAY)).willReturn(false);

    jobOn(LocalDate.of(2026, 6, 10)).resolveTheClosedMonthsIfDue();

    verify(service, never()).resolve(MARCH);
    verify(service, never()).resolve(APRIL);
    verify(service).resolve(MAY);
    verify(service, never()).resolve(YearMonth.of(2026, 6));
  }

  @Test
  void aTriggerReResolvesEveryClosedMonthSinceTheFirstPublishedNavOnAnyDay() {
    jobOn(LocalDate.of(2026, 6, 17)).reResolveEveryClosedMonth();

    verify(service).resolve(MARCH);
    verify(service).resolve(APRIL);
    verify(service).resolve(MAY);
    verify(service, never()).resolve(YearMonth.of(2026, 6));
  }

  @Test
  void aTriggerJustAfterMidnightInTallinnResolvesTheMonthThatJustClosedThoughTheClockRunsInUtc() {
    var halfPastMidnightOnTheFirstOfJune =
        LocalDate.of(2026, 6, 1).atTime(0, 30).atZone(TALLINN).toInstant();
    var job =
        new InstrumentFeeRateJob(
            service,
            notifier,
            new BusinessDays(new PublicHolidays()),
            Clock.fixed(halfPastMidnightOnTheFirstOfJune, ZoneOffset.UTC));

    job.reResolveEveryClosedMonth();

    verify(service).resolve(MAY);
  }

  @Test
  void oneMonthFailingIsAnnouncedAndDoesNotStopTheLaterMonths() {
    given(service.hasRatesResolvedAfterItClosed(any())).willReturn(false);
    willThrow(new IllegalStateException("boom")).given(service).resolve(APRIL);
    var marchRates = List.of(rate(MARCH));
    var mayRates = List.of(rate(MAY));
    given(service.resolve(MARCH)).willReturn(marchRates);
    given(service.resolve(MAY)).willReturn(mayRates);

    jobOn(THIRD_BUSINESS_DAY_OF_JUNE).resolveTheClosedMonthsIfDue();

    verify(notifier)
        .announce(
            List.of(
                new Resolved(MARCH, marchRates),
                new Failed(APRIL, "IllegalStateException"),
                new Resolved(MAY, mayRates)));
  }

  private InstrumentFeeRateJob jobOn(LocalDate today) {
    var clock = Clock.fixed(today.atTime(7, 0).atZone(TALLINN).toInstant(), TALLINN);
    return new InstrumentFeeRateJob(
        service, notifier, new BusinessDays(new PublicHolidays()), clock);
  }

  private static InstrumentRate rate(YearMonth period) {
    return new InstrumentRate(
        1L,
        "ZZ0000000001",
        period,
        new BigDecimal("0.00070000"),
        new BigDecimal("0.00070000"),
        AGREEMENT,
        null,
        NONE);
  }
}
