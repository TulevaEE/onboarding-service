package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService.SOURCE_HISTORICAL_IMPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.TransactionExecutionMapper.SOURCE_SEB_OOTEL;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import java.time.LocalDate;
import java.util.Optional;

enum ReconciliationMode {
  LIVE {
    @Override
    boolean countsAsListed(TransactionExecution execution, LocalDate asOfDate) {
      return true;
    }

    @Override
    Optional<LocalDate> restampedReportedDate(TransactionExecution existing, LocalDate asOfDate) {
      return Optional.empty();
    }
  },
  REPLAY {
    @Override
    boolean countsAsListed(TransactionExecution execution, LocalDate asOfDate) {
      LocalDate reportedDate = execution.getReportedDate();
      return SOURCE_SEB_OOTEL.equals(execution.getSource())
          && (reportedDate == null || !reportedDate.isAfter(asOfDate));
    }

    @Override
    Optional<LocalDate> restampedReportedDate(TransactionExecution existing, LocalDate asOfDate) {
      LocalDate reportedDate = existing.getReportedDate();
      if (SOURCE_HISTORICAL_IMPORT.equals(existing.getSource())
          || reportedDate == null
          || reportedDate.isAfter(asOfDate)) {
        return Optional.of(asOfDate);
      }
      return Optional.of(reportedDate);
    }
  };

  abstract boolean countsAsListed(TransactionExecution execution, LocalDate asOfDate);

  abstract Optional<LocalDate> restampedReportedDate(
      TransactionExecution existing, LocalDate asOfDate);
}
