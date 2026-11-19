package ee.tuleva.onboarding.comparisons.returns;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.overview.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.decampo.xirr.Xirr;
import org.jspecify.annotations.Nullable;

@Slf4j
final class InternalRateOfReturn {

  private static final int RETURN_DECIMAL_PLACES = 4;

  private InternalRateOfReturn() {}

  static @Nullable BigDecimal of(List<Transaction> transactions) {
    if (allZero(transactions)) {
      return ZERO;
    }
    // wish the author of this great library used interfaces instead
    try {
      List<org.decampo.xirr.Transaction> xirrInternalTransactions =
          transactions.stream()
              .map(InternalRateOfReturn::xirrTransactionFromInternalTransaction)
              .toList();

      double result = new Xirr(xirrInternalTransactions).xirr();
      return roundPercentage(result);
    } catch (IllegalArgumentException | ArithmeticException e) {
      log.warn(
          "XIRR solver found no rate: transactionCount={}, reason={}, message={}",
          transactions.size(),
          e.getClass().getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  private static boolean allZero(List<Transaction> transactions) {
    return transactions.stream().allMatch(transaction -> ZERO.compareTo(transaction.amount()) == 0);
  }

  private static BigDecimal roundPercentage(double percentage) {
    // ok to lose the precision here, doing presentational formatting here.
    return BigDecimal.valueOf(percentage).setScale(RETURN_DECIMAL_PLACES, RoundingMode.HALF_UP);
  }

  private static org.decampo.xirr.Transaction xirrTransactionFromInternalTransaction(
      Transaction transaction) {
    return new org.decampo.xirr.Transaction(
        transaction.amount().doubleValue(), Date.from(transaction.time()));
  }
}
