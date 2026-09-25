package ee.tuleva.onboarding.investment.instrument;

import static ee.tuleva.onboarding.investment.JobRunSchedule.INSTRUMENT_RETIREMENT;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;

import ee.tuleva.onboarding.instrument.InstrumentRetirement;
import ee.tuleva.onboarding.instrument.InstrumentRetirementOutcome;
import ee.tuleva.onboarding.investment.instrument.InstrumentRetirementCandidateFinder.RetirementCandidate;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentRetirementJob {

  private static final String CHECK_COULD_NOT_RUN =
      "INSTRUMENT RETIREMENT CHECK COULD NOT RUN — nothing was retired today; the cause is in the"
          + " application log. The job tries again the next working day.";

  private final InstrumentRetirementCandidateFinder retirementCandidateFinder;
  private final InstrumentRetirement instrumentRetirement;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = INSTRUMENT_RETIREMENT, zone = TIMEZONE)
  @SchedulerLock(name = "InstrumentRetirementJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void retireInstrumentsOffTheBooks() {
    List<RetirementCandidate> candidates;
    try {
      candidates = retirementCandidateFinder.findCandidates();
    } catch (RuntimeException e) {
      log.error(
          "Instrument retirement check could not run, nothing was retired: exception={}",
          e.getClass().getSimpleName(),
          e);
      notificationService.sendMessage(CHECK_COULD_NOT_RUN, INVESTMENT, ERROR);
      return;
    }
    retire(candidates);
  }

  private void retire(List<RetirementCandidate> candidates) {
    var outcome =
        instrumentRetirement.retire(candidates.stream().map(RetirementCandidate::isin).toList());

    if (outcome.isEmpty()) {
      return;
    }

    notificationService.sendMessage(formatRetirements(candidates, outcome), INVESTMENT);
  }

  private static String formatRetirements(
      List<RetirementCandidate> candidates, InstrumentRetirementOutcome outcome) {
    var message = new StringBuilder();
    if (!outcome.retiredIsins().isEmpty()) {
      message.append(
          "INSTRUMENT RETIRED — neither held nor in a model for long enough that active is now"
              + " false, so prices are no longer imported or checked\n");
      candidates.stream()
          .filter(candidate -> outcome.retiredIsins().contains(candidate.isin()))
          .forEach(candidate -> message.append("  %s\n".formatted(candidate.describe())));
      message.append("Stored prices and findByIsin are unaffected.\n");
      if (outcome.retiredWithoutReloadingThisInstance()) {
        message.append(
            "The instrument cache on this instance could not be reloaded, so imports and checks"
                + " stop within the hour rather than immediately.\n");
      }
    }
    if (!outcome.refusals().isEmpty()) {
      message.append(
          "COULD NOT RETIRE — fix the instrument reference data; the job tries again the next"
              + " working day\n");
      outcome.refusals().forEach(refusal -> message.append("  %s\n".formatted(refusal.describe())));
    }
    return message.toString().stripTrailing();
  }
}
