package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.investment.JobRunSchedule.INVESTMENT_REPORT_PUBLICATION_CHECK;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
class InvestmentReportPublicationCheckJob {

  private static final int LAST_MONTHS_REPORTS_REQUIRED_FROM_DAY = 10;

  private final InvestmentReportPublicationCheck check;
  private final InvestmentReportPublicationNotifier notifier;
  private final Clock clock;

  @Scheduled(cron = INVESTMENT_REPORT_PUBLICATION_CHECK, zone = TIMEZONE)
  @SchedulerLock(
      name = "InvestmentReportPublicationCheckJob",
      lockAtMostFor = "PT15M",
      lockAtLeastFor = "PT1M")
  void checkTheLatestDueReportsArePublished() {
    var today = LocalDate.now(clock);
    var month = monthRequiredOn(today);
    var publications = check.check(month);
    var allPublished = publications.stream().allMatch(ReportPublication::isPublished);
    log.info(
        "Investment report publication checked: month={}, allPublished={}", month, allPublished);
    if (today.getDayOfMonth() == LAST_MONTHS_REPORTS_REQUIRED_FROM_DAY || !allPublished) {
      notifier.notify(month, publications);
    }
  }

  private static YearMonth monthRequiredOn(LocalDate today) {
    var monthsBack = today.getDayOfMonth() < LAST_MONTHS_REPORTS_REQUIRED_FROM_DAY ? 2 : 1;
    return YearMonth.from(today).minusMonths(monthsBack);
  }
}
