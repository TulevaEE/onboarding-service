package ee.tuleva.onboarding.banking.payment;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record PaymentStatusReport(
    @Nullable String groupStatus, List<TransactionStatus> transactionStatuses) {
  public record TransactionStatus(
      String endToEndId, PaymentStatus status, @Nullable String reasonCode) {}

  public boolean isFileLevelOnly() {
    return transactionStatuses.isEmpty();
  }

  public List<TransactionStatus> rejections() {
    return transactionStatuses.stream().filter(t -> t.status().isRejection()).toList();
  }
}
