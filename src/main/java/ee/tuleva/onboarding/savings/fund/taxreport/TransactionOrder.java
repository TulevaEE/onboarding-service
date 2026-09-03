package ee.tuleva.onboarding.savings.fund.taxreport;

import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.account.transaction.Transaction;
import java.util.Comparator;

final class TransactionOrder {

  static final Comparator<Transaction> ACQUISITIONS_FIRST_WITHIN_AN_INSTANT =
      comparing(Transaction::time)
          .thenComparing(transaction -> transaction.isAcquisition() ? 0 : 1);

  private TransactionOrder() {}
}
