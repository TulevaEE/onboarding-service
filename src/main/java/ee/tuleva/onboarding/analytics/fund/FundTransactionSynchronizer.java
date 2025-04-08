package ee.tuleva.onboarding.analytics.fund;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
  private final FundTransactionRepository repository;

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
        log.info(
            "No transactions retrieved from EPIS for the period {}-{}. Skipping delete and insert.",
            startDate,
            endDate);
        return;
      }

      log.info(
          "Deleting existing transactions for ISIN {} between {} and {}",
          fundIsin,
          startDate,
          endDate);
      int deletedCount =
          repository.deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate);
      log.info(
          "Deleted {} existing transactions for ISIN {} between {} and {}",
          deletedCount,
          fundIsin,
          startDate,
          endDate);

      List<FundTransaction> entitiesToInsert =
          fundTransactions.stream()
              .map(dto -> convertToEntity(dto, fundIsin))
              .collect(Collectors.toList());

      if (!entitiesToInsert.isEmpty()) {
        repository.saveAll(entitiesToInsert);
        log.info(
            "Successfully inserted {} new fund transactions for ISIN {} between {} and {}.",
            entitiesToInsert.size(),
            fundIsin,
            startDate,
            endDate);
      }

      log.info(
          "Fund transaction synchronization completed for ISIN {} {}-{}: {} deleted, {} inserted.",
          fundIsin,
          startDate,
          endDate,
          deletedCount,
          entitiesToInsert.size());

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
