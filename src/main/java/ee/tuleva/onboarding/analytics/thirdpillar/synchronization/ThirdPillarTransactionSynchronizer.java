package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.PensionTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPillarTransactionSynchronizer {

  private final EpisService episService;
  private final AnalyticsThirdPillarTransactionRepository repository;

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

      Map<Boolean, List<AnalyticsThirdPillarTransaction>> partitioned =
          pensionTransactions.stream()
              .map(transaction -> convertToEntity(transaction))
              .collect(
                  Collectors.partitioningBy(
                      entity ->
                          repository
                              .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                                  entity.getReportingDate(),
                                  entity.getPersonalId(),
                                  entity.getTransactionType(),
                                  entity.getTransactionValue(),
                                  entity.getShareAmount())));

      List<AnalyticsThirdPillarTransaction> duplicates = partitioned.get(true);
      List<AnalyticsThirdPillarTransaction> toInsert = partitioned.get(false);

      toInsert.forEach(repository::save);
      log.info(
          "Synchronization completed: {} new transactions inserted, {} duplicates skipped.",
          toInsert.size(),
          duplicates.size());
    } catch (Exception e) {
      log.error("Synchronization failed: {}", e.getMessage());
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
        .dateCreated(LocalDateTime.now())
        .build();
  }
}
