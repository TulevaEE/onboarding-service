package ee.tuleva.onboarding.investment.check.limit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnershipLimitCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate FOURTH_BUSINESS_DAY_OF_OCTOBER = LocalDate.of(2026, 10, 6);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  @Mock private OwnershipLimitCheckService service;
  @Mock private OwnershipLimitCheckNotifier notifier;

  @Test
  void onTheFourthBusinessDay_checksTheClosedMonthAndPostsTheResult() {
    var run = new OwnershipCheckRun(SEPTEMBER, List.of(), List.of());
    given(service.everyFundIsChecked(SEPTEMBER)).willReturn(false);
    given(service.checkMonthEnd(SEPTEMBER)).willReturn(run);

    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER).checkClosedMonthUntilEveryFundIsChecked();

    then(notifier).should().notify(run);
  }

  @Test
  void aMonthStillUncheckedAfterTheFourthBusinessDay_isCaughtUpOnTheNextRun() {
    var run = new OwnershipCheckRun(SEPTEMBER, List.of(), List.of());
    given(service.everyFundIsChecked(SEPTEMBER)).willReturn(false);
    given(service.checkMonthEnd(SEPTEMBER)).willReturn(run);

    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER.plusDays(2)).checkClosedMonthUntilEveryFundIsChecked();

    then(notifier).should().notify(run);
  }

  @Test
  void aMonthAlreadyChecked_isNotCheckedOrPostedAgain() {
    given(service.everyFundIsChecked(SEPTEMBER)).willReturn(true);

    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER.plusDays(1)).checkClosedMonthUntilEveryFundIsChecked();

    then(service).should(never()).checkMonthEnd(any());
    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void beforeTheFourthBusinessDay_doesNothing() {
    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER.minusDays(1)).checkClosedMonthUntilEveryFundIsChecked();

    then(service).shouldHaveNoInteractions();
    then(notifier).shouldHaveNoInteractions();
  }

  @Test
  void aManualTrigger_checksTheClosedMonthEvenWhenItWasAlreadyChecked() {
    var run = new OwnershipCheckRun(SEPTEMBER, List.of(), List.of());
    given(service.checkMonthEnd(SEPTEMBER)).willReturn(run);

    jobOn(LocalDate.of(2026, 10, 20)).onOwnershipLimitCheckRequested();

    then(notifier).should().notify(run);
  }

  @Test
  void aRunThatThrows_postsTheFailureInsteadOfStayingSilent() {
    var failure = new IllegalStateException("database unavailable");
    given(service.everyFundIsChecked(SEPTEMBER)).willReturn(false);
    willThrow(failure).given(service).checkMonthEnd(SEPTEMBER);

    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER).checkClosedMonthUntilEveryFundIsChecked();

    then(notifier).should().notifyFailed(SEPTEMBER, failure);
    then(notifier).should(never()).notify(any());
  }

  @Test
  void aCatchUpGateThatThrows_postsTheFailureInsteadOfStayingSilent() {
    var failure = new IllegalStateException("database unavailable");
    willThrow(failure).given(service).everyFundIsChecked(SEPTEMBER);

    jobOn(FOURTH_BUSINESS_DAY_OF_OCTOBER).checkClosedMonthUntilEveryFundIsChecked();

    then(notifier).should().notifyFailed(SEPTEMBER, failure);
    then(service).should(never()).checkMonthEnd(any());
  }

  private OwnershipLimitCheckJob jobOn(LocalDate today) {
    var clock = Clock.fixed(today.atTime(7, 45).atZone(TALLINN).toInstant(), TALLINN);
    return new OwnershipLimitCheckJob(
        service, notifier, new BusinessDays(new PublicHolidays()), clock);
  }
}
