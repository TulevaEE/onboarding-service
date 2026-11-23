package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService.CREATED_BY_HISTORICAL_IMPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService.SOURCE_HISTORICAL_IMPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.TransactionExecutionMapper.navDateOfAnEtfFill;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HistoricalExecutionWriter {

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final TransactionExecutionRepository executionRepository;
  private final SebFundConfirmationNavDateWriter navDateWriter;

  void write(ParsedRow row, TransactionOrder order) {
    TransactionExecution execution = toExecution(row, order);
    executionRepository.save(execution);
    navDateWriter.writeTo(execution, order);
  }

  private TransactionExecution toExecution(ParsedRow row, TransactionOrder order) {
    return TransactionExecution.builder()
        .orderId(order.getId())
        .brokerTransactionId(row.brokerTransactionId())
        .executionTimestamp(row.executionTimestamp())
        .navDate(navDateOfAnEtfFill(row.instrumentType(), row.executionTimestamp()))
        .executedQuantity(row.executedQuantity())
        .unitPrice(row.unitPrice())
        .totalConsideration(row.totalConsideration())
        .settlementAmount(row.settlementAmount())
        .commissionAmount(row.commissionAmount())
        .scheduledSettlementDate(row.actualSettlementDate())
        .reportedDate(reportedDate(row))
        .source(SOURCE_HISTORICAL_IMPORT)
        .modifiedBy(CREATED_BY_HISTORICAL_IMPORT)
        .build();
  }

  private static @Nullable LocalDate reportedDate(ParsedRow row) {
    Instant known =
        row.executionTimestamp() == null ? row.orderTimestamp() : row.executionTimestamp();
    return known == null ? row.actualSettlementDate() : LocalDate.ofInstant(known, TALLINN);
  }
}
