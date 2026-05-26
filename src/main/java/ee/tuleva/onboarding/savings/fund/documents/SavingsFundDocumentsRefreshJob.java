package ee.tuleva.onboarding.savings.fund.documents;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SavingsFundDocumentsRefreshJob {

  private final SavingsFundDocumentsService savingsFundDocumentsService;

  @Scheduled(cron = "0 0 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SavingsFundDocumentsRefreshJob_refresh",
      lockAtMostFor = "55m",
      lockAtLeastFor = "1m")
  public void refresh() {
    savingsFundDocumentsService.refresh();
  }
}
