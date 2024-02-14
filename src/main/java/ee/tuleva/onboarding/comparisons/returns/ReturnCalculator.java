package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static java.time.ZoneId.systemDefault;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
public class ReturnCalculator {

  private static final int RETURN_DECIMAL_PLACES = 4;
  private static final int CASH_DECIMAL_PLACES = 2;

  private final FundValueProvider fundValueProvider;

  public ReturnDto getReturn(AccountOverview accountOverview) {
    BigDecimal rateOfReturn = getRateOfReturn(accountOverview);
    CashReturn cashReturn = getCashReturn(accountOverview);
    LocalDate from =
        accountOverview.sort().getFirstTransactionDate().orElse(accountOverview.getStartDate());
    return new ReturnDto(rateOfReturn, cashReturn.value, cashReturn.paymentsSum, EUR, from);
  }

  public ReturnDto getReturn(AccountOverview accountOverview, String comparisonFund) {
    CashReturn cashReturn = getCashReturn(accountOverview, comparisonFund);
    BigDecimal rateOfReturn = getRateOfReturn(accountOverview, comparisonFund);
    LocalDate from =
        accountOverview.sort().getFirstTransactionDate().orElse(accountOverview.getStartDate());
    return new ReturnDto(rateOfReturn, cashReturn.value, cashReturn.paymentsSum, EUR, from);
  }

  private BigDecimal getRateOfReturn(AccountOverview accountOverview) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    return calculateReturn(
        purchaseTransactions, accountOverview.getEndingBalance(), accountOverview.getEndTime());
  }

  private BigDecimal getRateOfReturn(AccountOverview accountOverview, String comparisonFund) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    val sellAmount =
        getSimulatedEndingBalanceForAFund(accountOverview, comparisonFund, purchaseTransactions);

    if (sellAmount.isEmpty()) {
      return ZERO;
    }

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

  private record CashReturn(BigDecimal paymentsSum, BigDecimal value) {
    public CashReturn() {
      this(ZERO, ZERO);
    }
  }

  private CashReturn getCashReturn(AccountOverview accountOverview) {
    val paymentsSum =
        accountOverview.getTransactions().stream()
            .map(Transaction::amount)
            .reduce(BigDecimal::add)
            .orElse(ZERO);

    BigDecimal cashReturn =
        accountOverview
            .getEndingBalance()
            .subtract(accountOverview.getBeginningBalance())
            .subtract(paymentsSum)
            .setScale(CASH_DECIMAL_PLACES, RoundingMode.HALF_UP);

    return new CashReturn(paymentsSum, cashReturn);
  }

  private CashReturn getCashReturn(AccountOverview accountOverview, String comparisonFund) {
    List<Transaction> purchaseTransactions = getPurchaseTransactions(accountOverview);

    val endingBalance =
        getSimulatedEndingBalanceForAFund(accountOverview, comparisonFund, purchaseTransactions);

    if (endingBalance.isEmpty()) {
      return new CashReturn();
    }

    val paymentsSum =
        accountOverview.getTransactions().stream()
            .map(Transaction::amount)
            .reduce(BigDecimal::add)
            .orElse(ZERO);

    BigDecimal cashReturn =
        endingBalance
            .orElseThrow()
            .subtract(accountOverview.getBeginningBalance())
            .subtract(paymentsSum)
            .setScale(CASH_DECIMAL_PLACES, RoundingMode.HALF_UP);

    return new CashReturn(paymentsSum, cashReturn);
  }

  private List<Transaction> getPurchaseTransactions(AccountOverview accountOverview) {
    List<Transaction> transactions = accountOverview.getTransactions();
    List<Transaction> purchaseTransactions = new ArrayList<>();

    //if (accountOverview.getBeginningBalance().compareTo(ZERO) != 0) {
    if (!accountOverview.getBeginningBalance().equals(ZERO)) {
      Transaction beginningTransaction =
          new Transaction(accountOverview.getBeginningBalance(), accountOverview.getStartTime());
      purchaseTransactions.add(beginningTransaction);
    }
    purchaseTransactions.addAll(transactions);

    return purchaseTransactions;
  }

  private List<Transaction> negateTransactionAmounts(List<Transaction> transactions) {
    return transactions.stream().map(ReturnCalculator::negateTransactionAmount).toList();
  }

  private static Transaction negateTransactionAmount(Transaction transaction) {
    return new Transaction(transaction.amount().negate(), transaction.time());
  }

  private BigDecimal calculateReturn(
      List<Transaction> purchaseTransactions, BigDecimal endingBalance, Instant endTime) {
    List<Transaction> negatedTransactions = negateTransactionAmounts(purchaseTransactions);
    Transaction endingTransaction = new Transaction(endingBalance, endTime);

    List<Transaction> internalTransactions = new ArrayList<>(negatedTransactions);
    internalTransactions.add(endingTransaction);

    return calculateInternalRateOfReturn(internalTransactions);
  }

  private BigDecimal calculateInternalRateOfReturn(List<Transaction> transactions) {
    if (allZero(transactions)) {
      return ZERO;
    }
    // wish the author of this great library used interfaces instead
    try {
      List<org.decampo.xirr.Transaction> xirrInternalTransactions =
          transactions.stream()
              .map(ReturnCalculator::xirrTransactionFromInternalTransaction)
              .toList();

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

  private BigDecimal roundPercentage(double percentage) {
    // ok to lose the precision here, doing presentational formatting here.
    return BigDecimal.valueOf(percentage).setScale(RETURN_DECIMAL_PLACES, RoundingMode.HALF_UP);
  }

  private static org.decampo.xirr.Transaction xirrTransactionFromInternalTransaction(
      Transaction transaction) {
    return new org.decampo.xirr.Transaction(
        transaction.amount().doubleValue(), Date.from(transaction.time()));
  }
}
