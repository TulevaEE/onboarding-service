package ee.tuleva.onboarding.analytics.exchange;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeTransactionSynchronizer {

  private final EpisService episService;
  private final ExchangeTransactionRepository repository;

  public void syncTransactions(
      LocalDate startDate,
      Optional<String> securityFrom,
      Optional<String> securityTo,
      boolean pikFlag) {

    log.info(
        "Starting synchronization of exchange transactions from startDate={}, securityFrom={}, securityTo={}, pikFlag={}",
        startDate,
        securityFrom.orElse(""),
        securityTo.orElse(""),
        pikFlag);

    try {
      List<ExchangeTransactionDto> exchangeTransactionDtos =
          episService.getExchangeTransactions(startDate, securityFrom, securityTo, pikFlag);
      log.info("Retrieved {} exchange transactions", exchangeTransactionDtos.size());

      List<ExchangeTransaction> analyticsTransactions =
          exchangeTransactionDtos.stream()
              .map(tx -> convertToEntity(tx, startDate))
              .collect(Collectors.toList());

      Map<Boolean, List<ExchangeTransaction>> partitioned =
          analyticsTransactions.stream()
              .collect(
                  Collectors.partitioningBy(
                      entity ->
                          repository
                              .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                                  entity.getReportingDate(),
                                  entity.getSecurityFrom(),
                                  entity.getSecurityTo(),
                                  entity.getCode(),
                                  entity.getUnitAmount(),
                                  entity.getPercentage())));

      List<ExchangeTransaction> duplicates = partitioned.get(true);
      List<ExchangeTransaction> toInsert = partitioned.get(false);

      toInsert.forEach(repository::save);
      log.info(
          "Synchronization completed: {} new transactions inserted, {} duplicates skipped.",
          toInsert.size(),
          duplicates.size());
    } catch (Exception e) {
      log.error("Synchronization failed: {}", e.getMessage());
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
