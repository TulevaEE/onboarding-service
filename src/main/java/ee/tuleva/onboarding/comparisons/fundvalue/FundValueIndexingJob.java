package ee.tuleva.onboarding.comparisons.fundvalue;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundValueRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundValueIndexingJob {
    private final FundValueRepository fundValueRepository;
    private final List<FundValueRetriever> fundValueRetrievers;

    private static final Instant START_TIME = parseInstant("2002-01-01");

    @Scheduled(cron = "0 0 14 * * ?", zone = "Europe/Tallinn") // every day at 2 o clock
    public void runIndexingJob() {
        fundValueRetrievers.forEach(fundValueRetriever -> {
            ComparisonFund fund = fundValueRetriever.getRetrievalFund();
            log.info("Starting to update values for " + fund);
            Optional<FundValue> fundValue = fundValueRepository.findLastValueForFund(fund);
            if (fundValue.isPresent()) {
                Instant lastUpdateTime = fundValue.get().getTime();
                if (!isToday(lastUpdateTime)) {
                    log.info("Last update for comparison fund " + fund + " was before today, so updating until today");
                    loadAndPersistDataForStartTime(fundValueRetriever, lastUpdateTime);
                } else {
                    log.info("Last update for comparison fund " + fund + " was today, so not updating");
                }
            } else {
                log.info("No info for comparison fund " + fund + " so downloading all data until today");
                loadAndPersistDataForStartTime(fundValueRetriever, START_TIME);
            }
        });
    }

    private void loadAndPersistDataForStartTime(FundValueRetriever fundValueRetriever, Instant startTime) {
        Instant endTime = Instant.now();
        List<FundValue> valuesPulled = fundValueRetriever.retrieveValuesForRange(startTime, endTime);
        fundValueRepository.saveAll(valuesPulled);
        log.info("Successfully pulled and saved " + valuesPulled.size() + " fund values");
    }

    private static boolean isToday(Instant time) {
        LocalDate otherDate = instantToLocalDate(time);
        LocalDate today = instantToLocalDate(Instant.now());
        return otherDate.equals(today);
    }

    private static LocalDate instantToLocalDate(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.of("Europe/Tallinn")).toLocalDate();
    }

    private static Instant parseInstant(String format) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant();
        } catch (ParseException e) {
            e.printStackTrace();
            return Instant.now();
        }
    }

}
