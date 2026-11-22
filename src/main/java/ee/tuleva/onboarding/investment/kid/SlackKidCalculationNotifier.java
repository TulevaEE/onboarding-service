package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.time.DayOfWeek.MONDAY;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "investment.kid.digest.enabled", havingValue = "true")
class SlackKidCalculationNotifier implements KidCalculationNotifier {

  private static final DayOfWeek WEEKLY_REMINDER_OF_THE_MONTHS_WAITING_FOR_PUBLICATION = MONDAY;

  private final OperationsNotificationService notificationService;
  private final KidFunds kidFunds;
  private final KidCalculationRepository calculations;
  private final KidPrintedFiguresRepository printedFigures;
  private final Clock clock;

  @Override
  public void notifyRuns(List<KidFundRun> runs) {
    runs.forEach(this::notifyAlone);
  }

  private void notifyAlone(KidFundRun run) {
    try {
      messageFor(run)
          .ifPresent(
              message ->
                  notificationService.sendMessage(message.text(), INVESTMENT, message.severity()));
    } catch (RuntimeException e) {
      log.error(
          "Failed to send KID calculation digest: fund={}, months={}",
          run.fund(),
          run.stored().stream().map(StoredKidCalculation::month).toList(),
          e);
    }
  }

  private Optional<KidDigestMessage> messageFor(KidFundRun run) {
    var failedWith = run.failedWith();
    if (failedWith != null) {
      return Optional.of(new KidRunFailedDigest(run.fund(), failedWith));
    }
    if (run.storedOrFailedAMonth()) {
      return Optional.of(digest(run));
    }
    if (KidCalendar.today(clock).getDayOfWeek()
        != WEEKLY_REMINDER_OF_THE_MONTHS_WAITING_FOR_PUBLICATION) {
      return Optional.empty();
    }
    var waiting = calculations.findCompleteUnpublishedMonths(run.fund());
    return waiting.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new KidCalculationDigest(
                run.fund(),
                holdingPeriodYears(run.fund()),
                List.of(),
                Map.of(),
                waiting,
                List.of()));
  }

  private KidCalculationDigest digest(KidFundRun run) {
    return new KidCalculationDigest(
        run.fund(),
        holdingPeriodYears(run.fund()),
        run.stored(),
        printedFigures.findPrintedScenarios(
            run.stored().stream().map(StoredKidCalculation::id).toList()),
        calculations.findCompleteUnpublishedMonths(run.fund()),
        run.failedMonths());
  }

  private int holdingPeriodYears(TulevaFund fund) {
    return kidFunds.settingsFor(fund).recommendedHoldingPeriodYears();
  }
}
