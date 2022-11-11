package ee.tuleva.onboarding.comparisons.returns;

import static java.math.BigDecimal.ZERO;
import static java.time.ZoneId.systemDefault;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.decampo.xirr.Xirr;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateOfReturnCalculator {

  private static final int OUTPUT_SCALE = 4;

  private final FundValueProvider fundValueProvider;

  public double getRateOfReturn(AccountOverview accountOverview) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    return calculateReturn(
        purchaseTransactions, accountOverview.getEndingBalance(), accountOverview.getEndTime());
  }

  public double getRateOfReturn(AccountOverview accountOverview, String comparisonFund) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    val sellAmount =
        getSimulatedEndingBalanceForAFund(accountOverview, comparisonFund, purchaseTransactions);

    if (sellAmount.isEmpty()) return 0;
    else
      return calculateReturn(
          purchaseTransactions, sellAmount.orElseThrow(), accountOverview.getEndTime());
  }

  private Optional<BigDecimal> getSimulatedEndingBalanceForAFund(
      AccountOverview accountOverview,
      String comparisonFund,
      List<Transaction> purchaseTransactions) {
    BigDecimal virtualFundUnitsBought = ZERO;
    for (Transaction transaction : purchaseTransactions) {
      Optional<FundValue> fundValueAtTime =
          fundValueProvider.getLatestValue(comparisonFund, transaction.date());
      if (fundValueAtTime.isEmpty()) {
        return Optional.empty();
      }
      BigDecimal fundPriceAtTime = fundValueAtTime.get().getValue();
      BigDecimal currentlyBoughtVirtualFundUnits =
          transaction.amount().divide(fundPriceAtTime, MathContext.DECIMAL128);
      virtualFundUnitsBought = virtualFundUnitsBought.add(currentlyBoughtVirtualFundUnits);
    }
    Optional<FundValue> finalVirtualFundValue =
        fundValueProvider.getLatestValue(
            comparisonFund, accountOverview.getEndTime().atZone(systemDefault()).toLocalDate());
    if (finalVirtualFundValue.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal finalVirtualFundPrice = finalVirtualFundValue.get().getValue();
    BigDecimal sellAmount = finalVirtualFundPrice.multiply(virtualFundUnitsBought);
    return Optional.of(sellAmount);
  }

  public BigDecimal getCashReturn(AccountOverview accountOverview) {
    val paymentsSum =
        accountOverview.getTransactions().stream()
            .map(Transaction::amount)
            .reduce(BigDecimal::add)
            .orElse(BigDecimal.ZERO);

    return accountOverview
        .getEndingBalance()
        .subtract(accountOverview.getBeginningBalance())
        .subtract(paymentsSum);
  }

  public Optional<BigDecimal> getCashReturn(
      AccountOverview accountOverview, String comparisonFund) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    val endingBalance =
        getSimulatedEndingBalanceForAFund(accountOverview, comparisonFund, purchaseTransactions);
    if (endingBalance.isEmpty()) return Optional.empty();

    val paymentsSum =
        accountOverview.getTransactions().stream()
            .map(Transaction::amount)
            .reduce(BigDecimal::add)
            .orElse(BigDecimal.ZERO);

    return Optional.of(
        endingBalance
            .orElseThrow()
            .subtract(accountOverview.getBeginningBalance())
            .subtract(paymentsSum));
  }

  private List<Transaction> getPurchaseTransactions(AccountOverview accountOverview) {
    List<Transaction> transactions = accountOverview.getTransactions();
    List<Transaction> purchaseTransactions = new ArrayList<>();

    if (!accountOverview.getBeginningBalance().equals(ZERO)) {
      Transaction beginningTransaction =
          new Transaction(accountOverview.getBeginningBalance(), accountOverview.getStartTime());
      purchaseTransactions.add(beginningTransaction);
    }
    purchaseTransactions.addAll(transactions);

    return purchaseTransactions;
  }

  private List<Transaction> negateTransactionAmounts(List<Transaction> transactions) {
    return transactions.stream()
        .map(RateOfReturnCalculator::negateTransactionAmount)
        .collect(toList());
  }

  private static Transaction negateTransactionAmount(Transaction transaction) {
    return new Transaction(transaction.amount().negate(), transaction.time());
  }

  private double calculateReturn(
      List<Transaction> purchaseTransactions, BigDecimal endingBalance, Instant endTime) {
    List<Transaction> negatedTransactions = negateTransactionAmounts(purchaseTransactions);
    Transaction endingTransaction = new Transaction(endingBalance, endTime);

    List<Transaction> internalTransactions = new ArrayList<>(negatedTransactions);
    internalTransactions.add(endingTransaction);

    return calculateInternalRateOfReturn(internalTransactions);
  }

  private double calculateInternalRateOfReturn(List<Transaction> transactions) {
    if (allZero(transactions)) {
      return 0;
    }
    // wish the author of this great library used interfaces instead
    try {
      List<org.decampo.xirr.Transaction> xirrInternalTransactions =
          transactions.stream()
              .map(RateOfReturnCalculator::xirrTransactionFromInternalTransaction)
              .collect(toList());

      double result = new Xirr(xirrInternalTransactions).xirr();
      return roundPercentage(result);
    } catch (IllegalArgumentException e) {
      log.info("XIRR failed for Transactions: {}", transactions);
      throw new IllegalArgumentException("XIRR calculation failed, see logs for more details", e);
    }
  }

  private boolean allZero(List<Transaction> transactions) {
    return transactions.stream().allMatch(transaction -> ZERO.compareTo(transaction.amount()) == 0);
  }

  private double roundPercentage(double percentage) {
    // ok to lose the precision here, doing presentational formatting here.
    return BigDecimal.valueOf(percentage)
        .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static org.decampo.xirr.Transaction xirrTransactionFromInternalTransaction(
      Transaction transaction) {
    return new org.decampo.xirr.Transaction(
        transaction.amount().doubleValue(), Date.from(transaction.time()));
  }
}
