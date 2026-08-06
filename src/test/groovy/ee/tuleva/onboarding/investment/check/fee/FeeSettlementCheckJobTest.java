package ee.tuleva.onboarding.investment.check.fee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeSettlementCheckJobTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private FeeCheckService feeCheckService;

  @Test
  void doesNotRunOnTheFirstBusinessDayOfTheMonth() {
    jobOn(LocalDate.of(2026, 6, 1)).checkClosedMonthIfReady();

    verifyNoInteractions(feeCheckService);
  }

  // Month m is settled during the second business day's NAV run, at each fund's cutoff. A check
  // that morning would read a non-zero residual for every fund, every month.
  @Test
  void doesNotRunOnTheSecondBusinessDayWhileSettlementIsStillBeingPosted() {
    jobOn(LocalDate.of(2026, 6, 2)).checkClosedMonthIfReady();

    verifyNoInteractions(feeCheckService);
  }

  @Test
  void checksTheJustClosedMonthOnTheThirdBusinessDay() {
    var thirdBusinessDay = LocalDate.of(2026, 6, 3);

    jobOn(thirdBusinessDay).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            eq(List.of(TulevaFund.values())),
            eq(LocalDate.of(2026, 5, 1)),
            eq(LocalDate.of(2026, 4, 1)),
            eq(thirdBusinessDay));
  }

  @Test
  void skipsDaysAfterTheThirdBusinessDay() {
    jobOn(LocalDate.of(2026, 6, 4)).checkClosedMonthIfReady();

    verifyNoInteractions(feeCheckService);
  }

  // The cash leg trails a month behind: April's payment has landed by June, May's has not.
  @Test
  void checksTheCashLegAMonthBehindTheSettlementLeg() {
    var thirdBusinessDay = LocalDate.of(2026, 6, 3);

    jobOn(thirdBusinessDay).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            any(),
            eq(LocalDate.of(2026, 5, 1)),
            eq(LocalDate.of(2026, 4, 1)),
            eq(thirdBusinessDay));
  }

  @Test
  void countsBusinessDaysPastAWeekendStartOfMonth() {
    jobOn(LocalDate.of(2026, 8, 5)).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            any(),
            eq(LocalDate.of(2026, 7, 1)),
            eq(LocalDate.of(2026, 6, 1)),
            eq(LocalDate.of(2026, 8, 5)));
  }

  @Test
  void aManualTriggerRunsTheClosedMonthRegardlessOfTheBusinessDay() {
    var notTheThirdBusinessDay = LocalDate.of(2026, 6, 17);

    jobOn(notTheThirdBusinessDay).onSettlementCheckRequested();

    verify(feeCheckService)
        .runMonthlyChecks(
            any(),
            eq(LocalDate.of(2026, 5, 1)),
            eq(LocalDate.of(2026, 4, 1)),
            eq(notTheThirdBusinessDay));
  }

  private FeeSettlementCheckJob jobOn(LocalDate today) {
    var clock = Clock.fixed(today.atStartOfDay(ESTONIAN_ZONE).toInstant(), ESTONIAN_ZONE);
    return new FeeSettlementCheckJob(
        feeCheckService, new BusinessDays(new PublicHolidays()), clock);
  }
}
