package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.PensionTransaction;
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
public class ThirdPillarTransactionSynchronizer {

  private final EpisService episService;
  private final AnalyticsThirdPillarTransactionRepository repository;

  @Transactional
  public void syncTransactions(LocalDate startDate, LocalDate endDate) {
    log.info("Starting synchronization for transactions from {} to {}", startDate, endDate);
    try {
      List<PensionTransaction> pensionTransactions =
          episService.getTransactions(startDate, endDate);
      log.info(
          "Retrieved {} transactions between {} and {}",
          pensionTransactions.size(),
          startDate,
          endDate);

      if (pensionTransactions.isEmpty()) {
        log.info(
            "No transactions retrieved from EPIS for the period {}-{}. Skipping delete and insert.",
            startDate,
            endDate);
        return;
      }

      log.info("Deleting existing transactions between {} and {}", startDate, endDate);
      int deletedCount = repository.deleteByReportingDateBetween(startDate, endDate);
      log.info(
          "Deleted {} existing transactions between {} and {}", deletedCount, startDate, endDate);

      List<AnalyticsThirdPillarTransaction> entitiesToInsert =
          pensionTransactions.stream().map(this::convertToEntity).collect(Collectors.toList());

      if (!entitiesToInsert.isEmpty()) {
        repository.saveAll(entitiesToInsert);
        log.info(
            "Successfully inserted {} new transactions between {} and {}.",
            entitiesToInsert.size(),
            startDate,
            endDate);
      }

      log.info(
          "Synchronization completed for {}-{}: {} deleted, {} inserted.",
          startDate,
          endDate,
          deletedCount,
          entitiesToInsert.size());

    } catch (Exception e) {
      log.error(
          "Synchronization failed for period {} to {}: {}", startDate, endDate, e.getMessage(), e);
    }
  }

  private AnalyticsThirdPillarTransaction convertToEntity(PensionTransaction transaction) {
    return AnalyticsThirdPillarTransaction.builder()
        .reportingDate(transaction.getDate())
        .fullName(transaction.getPersonName())
        .personalId(transaction.getPersonId())
        .accountNo(transaction.getPensionAccount())
        .country(transaction.getCountry())
        .transactionType(transaction.getTransactionType())
        .transactionSource(transaction.getPurpose())
        .applicationType(transaction.getApplicationType())
        .shareAmount(transaction.getUnitAmount())
        .sharePrice(transaction.getPrice())
        .nav(transaction.getNav())
        .transactionValue(transaction.getAmount())
        .serviceFee(transaction.getServiceFee())
        .fundManager(transaction.getFundManager())
        .fund(transaction.getFund())
        .purposeCode(transaction.getPurposeCode())
        .counterpartyName(transaction.getCounterpartyName())
        .counterpartyCode(transaction.getCounterpartyCode())
        .counterpartyBankAccount(transaction.getCounterpartyBankAccount())
        .counterpartyBank(transaction.getCounterpartyBank())
        .counterpartyBic(transaction.getCounterpartyBic())
        .dateCreated(LocalDateTime.now(ClockHolder.clock()))
        .build();
  }
}
