package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.event.RunInstrumentFeeRateResolveRequested;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Failed;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Resolved;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
class InstrumentFeeRateJob {

  private static final int THIRD_BUSINESS_DAY_A_DAY_BEFORE_OCF_AND_TD_ATTRIBUTION_READ_THE_RATES =
      3;
  private static final YearMonth FIRST_MONTH_WITH_A_PUBLISHED_NAV = YearMonth.of(2026, 3);

  private final InstrumentOcfService service;
  private final InstrumentFeeRateNotifier notifier;
  private final BusinessDays businessDays;
  private final Clock clock;

  @Scheduled(cron = "0 0 7 1-14 * *", zone = TIMEZONE)
  @SchedulerLock(name = "InstrumentFeeRateJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
  void resolveTheClosedMonthsIfDue() {
    var today = LocalDate.now(clock);
    if (!businessDays.isNthBusinessDayOfMonth(
        today, THIRD_BUSINESS_DAY_A_DAY_BEFORE_OCF_AND_TD_ATTRIBUTION_READ_THE_RATES)) {
      return;
    }
    resolveAndAnnounce(
        closedMonthsUpTo(today).filter(month -> !service.hasRatesResolvedAfterItClosed(month)));
  }

  @EventListener(RunInstrumentFeeRateResolveRequested.class)
  void reResolveEveryClosedMonth() {
    resolveAndAnnounce(closedMonthsUpTo(LocalDate.now(clock)));
  }

  private void resolveAndAnnounce(Stream<YearMonth> months) {
    var resolutions = months.map(this::resolved).toList();
    if (!resolutions.isEmpty()) {
      notifier.announce(resolutions);
    }
  }

  private MonthResolution resolved(YearMonth month) {
    try {
      log.info("Resolving instrument fee rates: period={}", month);
      return new Resolved(month, service.resolve(month));
    } catch (RuntimeException e) {
      log.error("Instrument fee rates could not be resolved: period={}", month, e);
      return new Failed(month, e.getClass().getSimpleName());
    }
  }

  private static Stream<YearMonth> closedMonthsUpTo(LocalDate today) {
    var lastClosedMonth = YearMonth.from(today).minusMonths(1);
    return Stream.iterate(
        FIRST_MONTH_WITH_A_PUBLISHED_NAV,
        month -> !month.isAfter(lastClosedMonth),
        month -> month.plusMonths(1));
  }
}
