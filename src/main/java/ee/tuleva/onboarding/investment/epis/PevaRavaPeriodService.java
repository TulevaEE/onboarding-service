package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DATA_VALID;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.TUK00_ACTIVE;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PevaRavaPeriodService {

  private static final int TUK75_SELL_BY_DAYS = 4;
  private static final int TUK00_SELL_BY_DAYS = 6;
  private static final int TUK75_D_ACTIVE_DAYS = 7;
  private static final int TUK00_D_ACTIVE_DAYS = 13;
  private static final int DONE_DAYS_AFTER_EXEC = 3;
  private static final int LAST_PERIOD_WINDOW_DAYS = 120;

  private final Target2Calendar target2Calendar;

  public List<PevaRavaCycle> executionPeriods(int year) {
    return List.of(
        cycle(LocalDate.of(year - 1, Month.NOVEMBER, 30), LocalDate.of(year, Month.JANUARY, 1)),
        cycle(LocalDate.of(year, Month.MARCH, 31), LocalDate.of(year, Month.MAY, 1)),
        cycle(LocalDate.of(year, Month.JULY, 31), LocalDate.of(year, Month.SEPTEMBER, 1)));
  }

  public PevaRavaPhase getCurrentPhase(LocalDate today) {
    return getCurrentPeriod(today).map(PevaRavaPeriod::phase).orElse(IGNORE);
  }

  public Optional<PevaRavaPeriod> getCurrentPeriod(LocalDate today) {
    List<PevaRavaCycle> allPeriods = new ArrayList<>();
    for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
      allPeriods.addAll(executionPeriods(year));
    }

    for (int i = 0; i < allPeriods.size(); i++) {
      PevaRavaCycle cycle = allPeriods.get(i);
      LocalDate nextLock =
          i + 1 < allPeriods.size()
              ? allPeriods.get(i + 1).lockDate()
              : cycle.execDate().plusDays(LAST_PERIOD_WINDOW_DAYS);

      if (today.isBefore(cycle.lockDate()) || !today.isBefore(nextLock)) {
        continue;
      }

      FundCycleTimeline tuk75 = timeline(cycle, today, TUK75_D_ACTIVE_DAYS, TUK75_SELL_BY_DAYS);
      FundCycleTimeline tuk00 = timeline(cycle, today, TUK00_D_ACTIVE_DAYS, TUK00_SELL_BY_DAYS);
      LocalDate doneDate = target2Calendar.addBusinessDays(cycle.execDate(), DONE_DAYS_AFTER_EXEC);

      return Optional.of(
          new PevaRavaPeriod(phase(today, tuk75, tuk00, doneDate), cycle, tuk75, tuk00));
    }
    return Optional.empty();
  }

  private static PevaRavaPhase phase(
      LocalDate today, FundCycleTimeline tuk75, FundCycleTimeline tuk00, LocalDate doneDate) {
    if (!today.isBefore(doneDate)) {
      return DONE;
    }
    if (tuk75.dActive()) {
      return ACTIVE;
    }
    if (tuk00.dActive()) {
      return TUK00_ACTIVE;
    }
    return DATA_VALID;
  }

  private FundCycleTimeline timeline(
      PevaRavaCycle cycle, LocalDate today, int dActiveDays, int sellByDays) {
    LocalDate dActiveDate = target2Calendar.subtractBusinessDays(cycle.execDate(), dActiveDays);
    LocalDate sellByDate = target2Calendar.subtractBusinessDays(cycle.execDate(), sellByDays);
    return new FundCycleTimeline(
        dActiveDate, sellByDate, !today.isBefore(dActiveDate), !today.isBefore(sellByDate));
  }

  private PevaRavaCycle cycle(LocalDate lockDate, LocalDate execDate) {
    return new PevaRavaCycle(lockDate, target2Calendar.nextOrSameBusinessDay(execDate));
  }
}
