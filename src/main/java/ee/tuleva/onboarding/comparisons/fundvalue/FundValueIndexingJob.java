package ee.tuleva.onboarding.comparisons.fundvalue;

import static java.util.Collections.emptyList;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetrieverFactory;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!dev")
public class FundValueIndexingJob {
  private final FundValueRepository fundValueRepository;
  private final List<ComparisonIndexRetriever> staticRetrievers;
  private final Environment environment;
  private final FundNavRetrieverFactory fundNavRetrieverFactory;
  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private List<ComparisonIndexRetriever> dynamicRetrievers = emptyList();

  static final LocalDate EARLIEST_DATE = LocalDate.parse("2003-01-07");

  @Scheduled(cron = "0 0 * * * *", zone = "Europe/Tallinn") // the top of every hour of every day
  @SchedulerLock(
      name = "FundValueIndexingJob_runIndexingJob",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void runIndexingJob() {
    refreshAll();
  }

  public void refreshAll() {
    log.info(
        "Running indexing job on retrievers: staticRetrievers={}, dynamicRetrievers={}",
        staticRetrievers,
        dynamicRetrievers);
    Stream.concat(staticRetrievers.stream(), dynamicRetrievers.stream())
        .forEach(this::refreshRetriever);
  }

  private void refreshRetriever(ComparisonIndexRetriever retriever) {
    String fund = retriever.getKey();
    if (retriever.requiresWorkingDay() && !publicHolidays.isWorkingDay(LocalDate.now(clock))) {
      log.info("Skipping {} on non-working day", fund);
      return;
    }
    log.info("Starting to update values for {}", fund);
    try {
      Optional<LocalDate> startDate = getStartDate(fund);
      if (startDate.isEmpty()) {
        return;
      }

      fetchAndSave(retriever, startDate.get());
    } catch (Exception e) {
      log.error("Failed to refresh retriever: fund={}", fund, e);
    }
  }

  private Optional<LocalDate> getStartDate(String fund) {
    LocalDate today = LocalDate.now(clock);
    Optional<FundValue> fundValue = fundValueRepository.findLastValueForFund(fund);

    if (fundValue.isEmpty()) {
      log.info("No info for comparison fund {} so downloading all data until today", fund);
      return Optional.of(EARLIEST_DATE);
    }

    LocalDate lastUpdate = fundValue.get().date();
    if (lastUpdate.equals(today)) {
      log.info("Last update for comparison fund {}: {}. Not updating", fund, lastUpdate);
      return Optional.empty();
    }

    LocalDate startDate = lastUpdate.plusDays(1);
    log.info(
        "Last update for comparison fund {}: {}. Updating from {}", fund, lastUpdate, startDate);

    if (lastUpdate.isBefore(today.minusWeeks(1))
        && (fund.startsWith("EE") || fund.startsWith("EPI"))) {
      log.error(
          "Last update for comparison fund {} is more than 1 week old. Last update: {}",
          fund,
          lastUpdate);
    }

    return Optional.of(startDate);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initDynamicRetrievers() {
    dynamicRetrievers = fundNavRetrieverFactory.createAll();
  }

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void runInitialIndexing() {
    if (!Arrays.asList(environment.getActiveProfiles()).contains("test")) {
      runIndexingJob();
    }
  }

  private void fetchAndSave(ComparisonIndexRetriever retriever, LocalDate startDate) {
    LocalDate endDate = LocalDate.now(clock);
    List<FundValue> valuesFetched = retriever.retrieveValuesForRange(startDate, endDate);
    List<FundValue> valuesSaved =
        valuesFetched.stream().map(fundValueRepository::save).flatMap(Optional::stream).toList();
    log.info(
        "Fund value indexing complete: key={}, fetched={}, saved={}",
        retriever.getKey(),
        valuesFetched.size(),
        valuesSaved.size());
  }
}
