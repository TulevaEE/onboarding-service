package ee.tuleva.onboarding.investment.check.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class FeeSettlementCheckJobTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final LocalDate JUNE_2026 = LocalDate.of(2026, 6, 1);

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

  // A monthly job that may be late needs "from the nth business day onwards", not "exactly on it".
  // One missed 10:00 run - a deploy, a restart, a ShedLock still held - used to drop the whole
  // month: no rows for that fee month for any fund, no notification, and nothing to signal the gap.
  @Test
  void stillRunsWhenTheThirdBusinessDayRunWasMissed() {
    var fourthBusinessDay = LocalDate.of(2026, 6, 4);

    jobOn(fourthBusinessDay).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            eq(List.of(TulevaFund.values())),
            eq(LocalDate.of(2026, 5, 1)),
            eq(LocalDate.of(2026, 4, 1)),
            eq(fourthBusinessDay));
  }

  @Test
  void keepsRunningToTheEndOfTheCronWindow() {
    var lastDayOfTheCronWindow = LocalDate.of(2026, 6, 14);

    jobOn(lastDayOfTheCronWindow).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            any(),
            eq(LocalDate.of(2026, 5, 1)),
            eq(LocalDate.of(2026, 4, 1)),
            eq(lastDayOfTheCronWindow));
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

  // The check is alert-only, so a failure inside it must not take down the scheduled run that
  // hosts it.
  @Test
  void swallowsAFailureInsideTheCheckSoTheScheduledRunSurvives() {
    var thirdBusinessDay = LocalDate.of(2026, 6, 3);
    doThrow(new IllegalStateException())
        .when(feeCheckService)
        .runMonthlyChecks(any(), any(), any(), any());

    var job = jobOn(thirdBusinessDay);

    assertThatCode(job::checkClosedMonthIfReady).doesNotThrowAnyException();
  }

  // A typo in the cron or the lock name is invisible to every behavioural test: the job would
  // simply never fire in production, silently.
  @Test
  void isScheduledAtTenTallinnTimeOverTheFirstFortnight() throws Exception {
    var scheduled = triggerMethod().getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 10 1-14 * *");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void holdsAUniquelyNamedSchedulerLock() throws Exception {
    assertThat(triggerMethod().getAnnotation(SchedulerLock.class).name())
        .isEqualTo("FeeSettlementCheckJob");
  }

  // The cron only fires on days 1-14, so the third business day has to land inside that window in
  // every month - including one that opens on a weekend followed by public holidays.
  @Test
  void theThirdBusinessDayAlwaysFallsWithinTheCronWindow() {
    var businessDays = new BusinessDays(new PublicHolidays());

    for (var month = LocalDate.of(2026, 1, 1);
        month.getYear() < 2029;
        month = month.plusMonths(1)) {
      assertThat(businessDays.nthBusinessDayOfMonth(month, 3).getDayOfMonth())
          .isLessThanOrEqualTo(14);
    }
  }

  // SettlementCompletenessChecker only escalates a stalled NAV pipeline to a warning once checkDate
  // has reached the settlement grace end, the fifth business day of the month after the fee month
  // (investment.fee-check.settlement-grace-business-days). A scheduled run has to still be firing
  // by then, or the one failure the check most needs to report is permanently out of reach and gets
  // filed as a benign "month not yet crossed".
  @Test
  void theScheduledRunStillFiresAtTheSettlementGraceEnd() {
    var graceEnd = new BusinessDays(new PublicHolidays()).nthBusinessDayOfMonth(JUNE_2026, 5);

    jobOn(graceEnd).checkClosedMonthIfReady();

    verify(feeCheckService)
        .runMonthlyChecks(
            any(), eq(LocalDate.of(2026, 5, 1)), eq(LocalDate.of(2026, 4, 1)), eq(graceEnd));
  }

  @Test
  void theSettlementGraceEndAlwaysFallsWithinTheCronWindow() {
    var businessDays = new BusinessDays(new PublicHolidays());

    for (var month = LocalDate.of(2026, 1, 1);
        month.getYear() < 2029;
        month = month.plusMonths(1)) {
      assertThat(businessDays.nthBusinessDayOfMonth(month, 5).getDayOfMonth())
          .isLessThanOrEqualTo(14);
    }
  }

  private Method triggerMethod() throws Exception {
    return FeeSettlementCheckJob.class.getDeclaredMethod("checkClosedMonthIfReady");
  }

  private FeeSettlementCheckJob jobOn(LocalDate today) {
    var clock = Clock.fixed(today.atStartOfDay(ESTONIAN_ZONE).toInstant(), ESTONIAN_ZONE);
    return new FeeSettlementCheckJob(
        feeCheckService, new BusinessDays(new PublicHolidays()), clock);
  }
}
