package ee.tuleva.onboarding.banking.payment;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a pain.002 told us.
 *
 * @param transactionStatuses one entry per payment the report carries a status for. Empty when the
 *     report only acknowledges the file as a whole — which is a real possibility we have not
 *     confirmed either way, so it is reported rather than assumed away.
 */
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
