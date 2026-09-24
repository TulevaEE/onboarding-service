package ee.tuleva.onboarding.investment.instrument;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.instrument.InstrumentRetirement;
import ee.tuleva.onboarding.investment.instrument.InstrumentRetirementCandidateFinder.RetirementCandidate;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentRetirementJob {

  private final InstrumentRetirementCandidateFinder retirementCandidateFinder;
  private final InstrumentRetirement instrumentRetirement;
  private final InstrumentReferenceService instrumentReferenceService;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 15 * * * *", zone = TIMEZONE)
  @SchedulerLock(name = "InstrumentRetirementJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void retireInstrumentsOffTheBooks() {
    var candidates = retirementCandidateFinder.findCandidates();
    if (candidates.isEmpty()) {
      return;
    }

    var retired = new ArrayList<RetirementCandidate>();
    var failed = new LinkedHashMap<String, String>();
    candidates.forEach(candidate -> retire(candidate, retired, failed));

    if (retired.isEmpty() && failed.isEmpty()) {
      return;
    }

    var cacheRefreshed = retired.isEmpty() || instrumentReferenceService.refresh();
    notificationService.sendMessage(formatRetirements(retired, failed, cacheRefreshed), INVESTMENT);
  }

  private void retire(
      RetirementCandidate candidate,
      List<RetirementCandidate> retired,
      Map<String, String> failed) {
    try {
      if (instrumentRetirement.retire(candidate.isin())) {
        retired.add(candidate);
      }
    } catch (Exception e) {
      log.error("Failed to retire instrument: isin={}", candidate.isin(), e);
      failed.put(candidate.isin(), String.valueOf(e.getMessage()));
    }
  }

  private static String formatRetirements(
      List<RetirementCandidate> retired, Map<String, String> failed, boolean cacheRefreshed) {
    var sb = new StringBuilder();
    if (!retired.isEmpty()) {
      sb.append(
          "INSTRUMENT RETIRED — off the books long enough that active is now false, so prices are"
              + " no longer imported or checked\n");
      retired.forEach(candidate -> sb.append(describe(candidate)));
      sb.append("Stored prices and findByIsin are unaffected.\n");
      if (!cacheRefreshed) {
        sb.append(
            "The instrument cache on this instance could not be reloaded, so imports and checks"
                + " stop within the hour rather than immediately.\n");
      }
    }
    if (!failed.isEmpty()) {
      sb.append("COULD NOT RETIRE — fix the instrument reference data\n");
      failed.forEach((isin, reason) -> sb.append("  %s — %s\n".formatted(isin, reason)));
    }
    return sb.toString().stripTrailing();
  }

  private static String describe(RetirementCandidate candidate) {
    return "  %s %s — last on the books %s, %d NAV dates ago\n"
        .formatted(
            candidate.isin(),
            candidate.displayName(),
            candidate.offTheBooksSince(),
            candidate.navDatesOffTheBooks());
  }
}
