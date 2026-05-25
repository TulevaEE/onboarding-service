package ee.tuleva.onboarding.statistics;

import java.util.List;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class InvestorCountGuardrailJob {

  private final InvestorStatisticsRepository investorStatisticsRepository;
  private final InvestorCountGuardrail investorCountGuardrail;

  @Scheduled(cron = "0 30 7 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "InvestorCountGuardrail_check",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  public void checkInvestorCount() {
    long current = investorStatisticsRepository.getActiveInvestorCount();
    OptionalLong previous = investorStatisticsRepository.getPreviousActiveInvestorCount();

    List<String> violations = investorCountGuardrail.findViolations(current, previous);
    if (violations.isEmpty()) {
      log.info("Investor count guardrail passed: count={}", current);
      return;
    }

    log.error(
        "Investor count guardrail failed: violations={}, source=analytics.mv_kpi_new shown on tuleva.ee front page",
        String.join("; ", violations));
  }
}
