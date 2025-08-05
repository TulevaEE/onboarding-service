package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeTransactionSnapshotService {

  private final ExchangeTransactionRepository currentTransactionRepository;
  private final ExchangeTransactionSnapshotRepository snapshotRepository;

  @Transactional
  public void takeSnapshot(String jobType) {
    OffsetDateTime jobStartTime = OffsetDateTime.now(ClockHolder.clock());
    takeSnapshot(jobType, jobStartTime);
  }

  @Transactional
  public void takeSnapshot(String jobType, OffsetDateTime snapshotTakenAt) {
    log.info(
        "Starting {} exchange transaction snapshot job taken at {}.", jobType, snapshotTakenAt);

    currentTransactionRepository
        .findTopByOrderByReportingDateDesc()
        .map(ExchangeTransaction::getReportingDate)
        .ifPresentOrElse(
            latestReportingDate -> {
              log.info(
                  "Latest reporting date determined as {} for {} job.",
                  latestReportingDate,
                  jobType);

              List<ExchangeTransaction> transactionsToSnapshot =
                  currentTransactionRepository.findByReportingDate(latestReportingDate);

              if (transactionsToSnapshot.isEmpty()) {
                log.info(
                    "No transactions found with the latest reporting date ({}) to snapshot for {} job.",
                    latestReportingDate,
                    jobType);
                return;
              }

              List<ExchangeTransactionSnapshot> snapshots =
                  transactionsToSnapshot.stream()
                      .map(
                          currentTransaction ->
                              ExchangeTransactionSnapshot.builder()
                                  .snapshotTakenAt(snapshotTakenAt)
                                  .createdAt(OffsetDateTime.now(ClockHolder.clock()))
                                  .reportingDate(currentTransaction.getReportingDate())
                                  .securityFrom(currentTransaction.getSecurityFrom())
                                  .securityTo(currentTransaction.getSecurityTo())
                                  .fundManagerFrom(currentTransaction.getFundManagerFrom())
                                  .fundManagerTo(currentTransaction.getFundManagerTo())
                                  .code(currentTransaction.getCode())
                                  .firstName(currentTransaction.getFirstName())
                                  .name(currentTransaction.getName())
                                  .percentage(currentTransaction.getPercentage())
                                  .unitAmount(currentTransaction.getUnitAmount())
                                  .sourceDateCreated(currentTransaction.getDateCreated())
                                  .build())
                      .collect(Collectors.toList());

              snapshotRepository.saveAll(snapshots);
              log.info(
                  "Successfully created {} exchange transaction snapshots for records with reporting date {} for {} job, taken at {}.",
                  snapshots.size(),
                  latestReportingDate,
                  jobType,
                  snapshotTakenAt);
            },
            () ->
                log.info(
                    "No current exchange transactions found to determine latest reporting date for {} job.",
                    jobType));
  }

  // Method to snapshot transactions from a specific reporting date for fixing previous period
  // issues
  @Transactional
  public void takeSnapshotForReportingDate(
      String jobType, OffsetDateTime snapshotTakenAt, LocalDate reportingDate) {
    log.info(
        "Starting {} exchange transaction snapshot job for reporting date {} taken at {}.",
        jobType,
        reportingDate,
        snapshotTakenAt);

    List<ExchangeTransaction> transactionsToSnapshot =
        currentTransactionRepository.findByReportingDate(reportingDate);

    if (transactionsToSnapshot.isEmpty()) {
      log.info(
          "No transactions found with reporting date ({}) to snapshot for {} job.",
          reportingDate,
          jobType);
      return;
    }

    List<ExchangeTransactionSnapshot> snapshots =
        transactionsToSnapshot.stream()
            .map(
                currentTransaction ->
                    ExchangeTransactionSnapshot.builder()
                        .snapshotTakenAt(snapshotTakenAt)
                        .createdAt(OffsetDateTime.now(ClockHolder.clock()))
                        .reportingDate(currentTransaction.getReportingDate())
                        .securityFrom(currentTransaction.getSecurityFrom())
                        .securityTo(currentTransaction.getSecurityTo())
                        .fundManagerFrom(currentTransaction.getFundManagerFrom())
                        .fundManagerTo(currentTransaction.getFundManagerTo())
                        .code(currentTransaction.getCode())
                        .firstName(currentTransaction.getFirstName())
                        .name(currentTransaction.getName())
                        .percentage(currentTransaction.getPercentage())
                        .unitAmount(currentTransaction.getUnitAmount())
                        .sourceDateCreated(currentTransaction.getDateCreated())
                        .build())
            .collect(Collectors.toList());

    snapshotRepository.saveAll(snapshots);
    log.info(
        "Successfully created {} exchange transaction snapshots for reporting date {} for {} job, taken at {}.",
        snapshots.size(),
        reportingDate,
        jobType,
        snapshotTakenAt);
  }
}
