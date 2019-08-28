package ee.tuleva.onboarding.comparisons.fundvalue;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.ComparisonIndexRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIndexingJob {
    private final FundValueRepository fundValueRepository;
    private final List<ComparisonIndexRetriever> comparisonIndexRetrievers;
    private final Environment environment;

    static final LocalDate EARLIEST_DATE = LocalDate.parse("2002-01-01");

    @Scheduled(cron = "0 0 14 * * ?", zone = "Europe/Tallinn") // every day at 2 o clock
    public void runIndexingJob() {
        comparisonIndexRetrievers.forEach(comparisonIndexRetriever -> {
            String fund = comparisonIndexRetriever.getKey();
            log.info("Starting to update values for " + fund);
            Optional<FundValue> fundValue = fundValueRepository.findLastValueForFund(fund);
            if (fundValue.isPresent()) {
                LocalDate lastUpdate = fundValue.get().getDate();
                if (!lastUpdate.equals(LocalDate.now())) {
                    LocalDate startDate = lastUpdate.plusDays(1);
                    log.info("Last update for comparison fund {}: {}. Updating from {}", fund, lastUpdate, startDate);
                    loadAndPersistDataForStartTime(comparisonIndexRetriever, startDate);
                } else {
                    log.info("Last update for comparison fund {}: {}. Not updating", fund, lastUpdate);
                }
            } else {
                log.info("No info for comparison fund {} so downloading all data until today", fund);
                loadAndPersistDataForStartTime(comparisonIndexRetriever, EARLIEST_DATE);
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class) // after every deploy
    public void onApplicationReady() {
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
