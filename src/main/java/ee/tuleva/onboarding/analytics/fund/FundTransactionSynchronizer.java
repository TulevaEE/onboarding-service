package ee.tuleva.onboarding.analytics.fund;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundTransactionSynchronizer {

  private final EpisService episService;
  private final AnalyticsFundTransactionRepository repository;

  @Transactional
  public void syncTransactions(String fundIsin, LocalDate startDate, LocalDate endDate) {
    log.info(
        "Starting fund transaction synchronization for ISIN {} from {} to {}",
        fundIsin,
        startDate,
        endDate);
    try {
      List<FundTransactionDto> fundTransactions =
          episService.getFundTransactions(fundIsin, startDate, endDate);
      log.info(
          "Retrieved {} fund transactions for ISIN {} between {} and {}",
          fundTransactions.size(),
          fundIsin,
          startDate,
          endDate);

      if (fundTransactions.isEmpty()) {
        log.info("No new fund transactions found for the period. Synchronization complete.");
        return;
      }

      Map<Boolean, List<FundTransaction>> partitioned =
          fundTransactions.stream()
              .map(fundTransactionDto -> convertToEntity(fundTransactionDto, fundIsin))
              .collect(
                  Collectors.partitioningBy(
                      entity ->
                          repository
                              .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
                                  entity.getTransactionDate(),
                                  entity.getPersonalId(),
                                  entity.getTransactionType(),
                                  entity.getAmount(),
                                  entity.getUnitAmount())));

      List<FundTransaction> duplicates = partitioned.get(true);
      List<FundTransaction> toInsert = partitioned.get(false);

      if (!duplicates.isEmpty()) {
        log.debug(
            "Skipping {} duplicate fund transactions based on unique key (date, personalId, type, amount, unitAmount).",
            duplicates.size());
      }

      if (!toInsert.isEmpty()) {
        repository.saveAll(toInsert);
        log.info("Successfully inserted {} new fund transactions.", toInsert.size());
      } else {
        log.info("No new unique fund transactions to insert.");
      }

      log.info(
          "Fund transaction synchronization completed: {} new transactions inserted, {} duplicates skipped.",
          toInsert.size(),
          duplicates.size());

    } catch (Exception e) {
      log.error(
          "Fund transaction synchronization failed for ISIN {} between {} and {}: {}",
          fundIsin,
          startDate,
          endDate,
          e.getMessage(),
          e);
    }
  }

  private FundTransaction convertToEntity(FundTransactionDto dto, String isin) {
    return FundTransaction.builder()
        .isin(isin)
        .transactionDate(dto.getDate())
        .personName(dto.getPersonName())
        .personalId(dto.getPersonId())
        .pensionAccount(dto.getPensionAccount())
        .country(dto.getCountry())
        .transactionType(dto.getTransactionType())
        .purpose(dto.getPurpose())
        .applicationType(dto.getApplicationType())
        .unitAmount(dto.getUnitAmount())
        .price(dto.getPrice())
        .nav(dto.getNav())
        .amount(dto.getAmount())
        .serviceFee(dto.getServiceFee())
        .dateCreated(LocalDateTime.now(ClockHolder.clock()))
        .build();
  }
}
