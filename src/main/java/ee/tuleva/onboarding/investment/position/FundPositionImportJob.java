package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.investment.position.parser.FundPositionParser;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
public class FundPositionImportJob {

  private static final int LOOKBACK_DAYS = 7;

  private final FundPositionSource source;
  private final FundPositionParser parser;
  private final FundPositionImportService importService;

  @Scheduled(cron = "0 0 7 * * *", zone = "Europe/Tallinn")
  @Scheduled(cron = "0 55 12 7 1 *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "FundPositionImportJob", lockAtMostFor = "55m", lockAtLeastFor = "5m")
  public void runImport() {
    LocalDate today = LocalDate.now();
    IntStream.rangeClosed(1, LOOKBACK_DAYS)
        .mapToObj(today::minusDays)
        .forEach(
            date -> {
              try {
                importForDate(date);
              } catch (Exception e) {
                log.error("Import failed, continuing with next date: date={}", date, e);
              }
            });
  }

  public void importForDate(LocalDate date) {
    if (importService.isDateAlreadyImported(date)) {
      log.debug("Skipping already imported date: date={}", date);
      return;
    }

    log.info("Starting fund position import: date={}", date);

    Optional<InputStream> csvStream = source.fetch(date);
    if (csvStream.isEmpty()) {
      log.warn("No fund position file found: date={}", date);
      return;
    }

    try (InputStream stream = csvStream.get()) {
      List<FundPosition> positions = parser.parse(stream);
      log.info("Parsed fund positions: date={}, count={}", date, positions.size());

      int imported = importService.importPositions(positions);
      log.info("Fund position import completed: date={}, imported={}", date, imported);

    } catch (Exception e) {
      log.error("Fund position import failed: date={}", date, e);
      throw new RuntimeException("Fund position import failed: date=" + date, e);
    }
  }
}
