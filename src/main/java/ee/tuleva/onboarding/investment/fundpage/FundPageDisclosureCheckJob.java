package ee.tuleva.onboarding.investment.fundpage;

import static ee.tuleva.onboarding.investment.JobRunSchedule.FUND_PAGE_DISCLOSURE_CHECK;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

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
class FundPageDisclosureCheckJob {

  private final FundPageDisclosureCheck check;
  private final FundPageDisclosureNotifier notifier;

  @Scheduled(cron = FUND_PAGE_DISCLOSURE_CHECK, zone = TIMEZONE)
  @SchedulerLock(
      name = "FundPageDisclosureCheckJob",
      lockAtMostFor = "PT15M",
      lockAtLeastFor = "PT1M")
  void checkTheFundPagesListEveryRequiredDocument() {
    var pages = check.check();
    var allComplete = FundPageDisclosures.allComplete(pages);
    log.info("Fund page disclosures checked: allComplete={}", allComplete);
    if (!allComplete) {
      notifier.notify(pages);
    }
  }
}
