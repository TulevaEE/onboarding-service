package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TransactionBatchResponse(
    Long id,
    TulevaFund fund,
    BatchStatus status,
    String createdBy,
    Instant createdAt,
    List<String> availableExports,
    Map<String, String> driveFileUrls,
    List<TransactionOrderResponse> orders,
    @Nullable Map<String, Object> calculationSnapshot) {

  static final Set<String> EXPORT_TYPES =
      Set.of("xlsxExport", "sebFundXlsx", "sebEtfXlsx", "ftEtfXlsx", "uuidWorkbookXlsx");

  static TransactionBatchResponse from(
      TransactionBatch batch,
      List<TransactionOrder> orders,
      @Nullable Map<String, Object> calculationSnapshot) {
    Map<String, Object> metadata = batch.getMetadata();
    return new TransactionBatchResponse(
        batch.getId(),
        batch.getFund(),
        batch.getStatus(),
        batch.getCreatedBy(),
        batch.getCreatedAt(),
        EXPORT_TYPES.stream().filter(metadata::containsKey).sorted().toList(),
        driveFileUrls(metadata),
        orders.stream().map(TransactionOrderResponse::from).toList(),
        calculationSnapshot);
  }

  private static Map<String, String> driveFileUrls(Map<String, Object> metadata) {
    if (metadata.get("driveFileUrls") instanceof Map<?, ?> urls) {
      return urls.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> String.valueOf(entry.getKey()),
                  entry -> String.valueOf(entry.getValue())));
    }
    return Map.of();
  }
}
