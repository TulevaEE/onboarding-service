package ee.tuleva.onboarding.analytics.exchange;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeTransactionSynchronizer {

  private final EpisService episService;
  private final ExchangeTransactionRepository repository;

  @Transactional
  public void syncTransactions(
      LocalDate startDate,
      Optional<String> securityFrom,
      Optional<String> securityTo,
      boolean pikFlag) {

    LocalDate reportingDate = startDate;

    log.info(
        "Starting synchronization of exchange transactions for reportingDate={}, securityFrom={}, securityTo={}, pikFlag={}",
        reportingDate,
        securityFrom.orElse("N/A"),
        securityTo.orElse("N/A"),
        pikFlag);

    try {
      List<ExchangeTransactionDto> exchangeTransactionDtos =
          episService.getExchangeTransactions(startDate, securityFrom, securityTo, pikFlag);
      log.info("Retrieved {} exchange transactions", exchangeTransactionDtos.size());

      if (exchangeTransactionDtos.isEmpty()) {
        log.info(
            "No transactions retrieved from EPIS for reportingDate {}. Skipping delete and insert.",
            reportingDate);
        return;
      }

      log.info("Deleting existing transactions for reportingDate {}", reportingDate);
      int deletedCount = repository.deleteByReportingDate(reportingDate);
      log.info(
          "Deleted {} existing transactions for reportingDate {}", deletedCount, reportingDate);

      List<ExchangeTransaction> entitiesToInsert =
          exchangeTransactionDtos.stream()
              .map(tx -> convertToEntity(tx, reportingDate))
              .collect(Collectors.toList());

      if (!entitiesToInsert.isEmpty()) {
        repository.saveAll(entitiesToInsert);
        log.info(
            "Successfully inserted {} new transactions for reportingDate {}.",
            entitiesToInsert.size(),
            reportingDate);
      }

      log.info(
          "Synchronization completed for reportingDate {}: {} deleted, {} inserted.",
          reportingDate,
          deletedCount,
          entitiesToInsert.size());

    } catch (Exception e) {
      log.error(
          "Synchronization failed for reportingDate {}: {}", reportingDate, e.getMessage(), e);
    }
  }

  private ExchangeTransaction convertToEntity(ExchangeTransactionDto tx, LocalDate reportingDate) {
    return ExchangeTransaction.builder()
        .reportingDate(reportingDate)
        .securityFrom(tx.getSecurityFrom())
        .securityTo(tx.getSecurityTo())
        .fundManagerFrom(tx.getFundManagerFrom())
        .fundManagerTo(tx.getFundManagerTo())
        .code(tx.getCode())
        .firstName(tx.getFirstName())
        .name(tx.getName())
        .percentage(tx.getPercentage())
        .unitAmount(tx.getUnitAmount())
        .dateCreated(LocalDateTime.now(ClockHolder.clock()))
        .build();
  }
}
