package ee.tuleva.onboarding.analytics.transaction.generic;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTransactionSynchronizer<DTO, E> {

  protected final EpisService episService;
  protected final TransactionTemplate transactionTemplate;

  protected abstract List<DTO> fetchTransactions(SyncContext context);

  protected abstract int deleteExistingTransactions(SyncContext context);

  protected abstract E convertToEntity(DTO dto, SyncContext context);

  protected abstract void saveEntities(List<E> entities);

  protected abstract String getTransactionTypeName();

  protected abstract String getSyncIdentifier(SyncContext context);

  protected SyncResult syncInternal(SyncContext context) {
    String transactionType = getTransactionTypeName();
    String syncIdentifier = getSyncIdentifier(context);
    log.info("Starting {} transaction synchronization for {}", transactionType, syncIdentifier);

    try {
      List<DTO> dtos = fetchTransactions(context);
      log.info("Retrieved {} {} transactions for {}", dtos.size(), transactionType, syncIdentifier);

      if (dtos.isEmpty()) {
        log.info(
            "No {} transactions retrieved from EPIS for {}. Skipping delete and insert.",
            transactionType,
            syncIdentifier);
        return new SyncResult(transactionType, syncIdentifier, 0, 0);
      }

      List<E> entitiesToInsert =
          dtos.stream().map(dto -> convertToEntity(dto, context)).collect(toList());

      Integer deletedCount =
          transactionTemplate.execute(
              status -> {
                log.info(
                    "Deleting existing {} transactions for {}", transactionType, syncIdentifier);
                int deleted = deleteExistingTransactions(context);
                log.info(
                    "Deleted {} existing {} transactions for {}",
                    deleted,
                    transactionType,
                    syncIdentifier);

                saveEntities(entitiesToInsert);
                log.info(
                    "Successfully inserted {} new {} transactions for {}.",
                    entitiesToInsert.size(),
                    transactionType,
                    syncIdentifier);
                return deleted;
              });

      log.info(
          "{} transaction synchronization completed for {}: {} deleted, {} inserted.",
          transactionType,
          syncIdentifier,
          deletedCount,
          entitiesToInsert.size());

      return new SyncResult(transactionType, syncIdentifier, deletedCount, entitiesToInsert.size());

    } catch (Exception e) {
      log.error(
          "{} transaction synchronization failed for {}: {}",
          transactionType,
          syncIdentifier,
          e.getMessage(),
          e);
      return new SyncResult(transactionType, syncIdentifier, 0, 0);
    }
  }

  protected LocalDateTime now() {
    return LocalDateTime.now(ClockHolder.clock());
  }
}
