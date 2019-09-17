package ee.tuleva.onboarding.comparisons.fundvalue;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundNavRetrieverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIndexingJob {
    private final FundValueRepository fundValueRepository;
    private final List<ComparisonIndexRetriever> staticRetrievers;
    private final Environment environment;
    private final FundNavRetrieverFactory fundNavRetrieverFactory;
    private List<ComparisonIndexRetriever> dynamicRetrievers = emptyList();

    static final LocalDate EARLIEST_DATE = LocalDate.parse("2003-01-07");

//    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Tallinn") // the top of every hour of every day
    @Scheduled(cron = "0 0 22 * * ?", zone = "Europe/Tallinn") // every day at 10pm
    public void runIndexingJob() {
        Stream.concat(staticRetrievers.stream(), dynamicRetrievers.stream()).forEach(retriever -> {
            String fund = retriever.getKey();
            log.info("Starting to update values for " + fund);
            Optional<FundValue> fundValue = fundValueRepository.findLastValueForFund(fund);
            if (fundValue.isPresent()) {
                LocalDate lastUpdate = fundValue.get().getDate();
                if (!lastUpdate.equals(LocalDate.now())) {
                    LocalDate startDate = lastUpdate.plusDays(1);
                    log.info("Last update for comparison fund {}: {}. Updating from {}", fund, lastUpdate, startDate);
                    loadAndPersistDataForStartTime(retriever, startDate);
                } else {
                    log.info("Last update for comparison fund {}: {}. Not updating", fund, lastUpdate);
                }
            } else {
                log.info("No info for comparison fund {} so downloading all data until today", fund);
                loadAndPersistDataForStartTime(retriever, EARLIEST_DATE);
            }
        });
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

    private void loadAndPersistDataForStartTime(ComparisonIndexRetriever comparisonIndexRetriever, LocalDate startDate) {
        LocalDate endDate = LocalDate.now();
        List<FundValue> valuesPulled = comparisonIndexRetriever.retrieveValuesForRange(startDate, endDate);
        fundValueRepository.saveAll(valuesPulled);
        log.info("Successfully pulled and saved " + valuesPulled.size() + " fund values");
    }
}
