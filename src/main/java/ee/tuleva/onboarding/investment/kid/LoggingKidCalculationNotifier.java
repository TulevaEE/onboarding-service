package ee.tuleva.onboarding.investment.kid;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "investment.kid.digest.enabled",
    havingValue = "false",
    matchIfMissing = true)
class LoggingKidCalculationNotifier implements KidCalculationNotifier {

  @Override
  public void notifyRuns(List<KidFundRun> runs) {
    runs.stream()
        .filter(run -> !run.stored().isEmpty() || run.failedWith() != null)
        .forEach(
            run ->
                log.info(
                    "KID calculation digest is off, not sent: fund={}, months={}, failedWith={}",
                    run.fund(),
                    run.stored().stream().map(StoredKidCalculation::month).toList(),
                    run.failedWith()));
  }
}
