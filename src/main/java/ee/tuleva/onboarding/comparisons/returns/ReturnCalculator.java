package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.AdjustedAcwiIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.decampo.xirr.Xirr;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnCalculator {

  private static final int RETURN_DECIMAL_PLACES = 4;
  private static final int CASH_DECIMAL_PLACES = 2;
  private static final Set<String> WORLD_INDEXES_PRICED_AT_US_CLOSE =
      Set.of(UnionStockIndexRetriever.KEY, AdjustedAcwiIndexRetriever.KEY);

  private static final int CALENDAR_DAYS_BEFORE_THE_FIRST_PURCHASE_FOR_ITS_PREVIOUS_CLOSE = 10;

  private final FundValueProvider fundValueProvider;
  private final FundPriceTiming fundPriceTiming;

  public ReturnDto getReturn(AccountOverview accountOverview) {
    BigDecimal rateOfReturn = getPersonalRateOfReturn(accountOverview);
    CashReturn cashReturn = getPersonalCashReturn(accountOverview);
    LocalDate from = accountOverview.calculateRealBeginningDate();
    LocalDate to = accountOverview.getEndDate();
    return new ReturnDto(rateOfReturn, cashReturn.value, cashReturn.paymentsSum, EUR, from, to);
  }

  public ReturnDto getSimulatedReturn(AccountOverview accountOverview, String comparisonFund) {
    LocalDate from = accountOverview.calculateRealBeginningDate();
    LocalDate to = accountOverview.getEndDate();
    return getSimulatedEndingBalanceForAFund(accountOverview, comparisonFund)
        .map(
            endingBalance -> {
              CashReturn cashReturn = getSimulatedCashReturn(accountOverview, endingBalance);
              return new ReturnDto(
                  getSimulatedRateOfReturn(accountOverview, endingBalance),
                  cashReturn.value,
                  cashReturn.paymentsSum,
                  EUR,
                  from,
                  to);
            })
        .orElseGet(() -> new ReturnDto(null, ZERO, ZERO, EUR, from, to));
  }

  @Nullable
  private BigDecimal getPersonalRateOfReturn(AccountOverview accountOverview) {
    return calculateReturn(
        getPurchaseTransactions(accountOverview),
        accountOverview.getEndingBalance(),
        accountOverview.getEndingBalanceTime());
  }

  @Nullable
  private BigDecimal getSimulatedRateOfReturn(
      AccountOverview accountOverview, BigDecimal sellAmount) {
    return calculateReturn(
        getPurchaseTransactions(accountOverview),
        sellAmount,
        accountOverview.getEndingBalanceTime());
  }

  private Optional<BigDecimal> getSimulatedEndingBalanceForAFund(
      AccountOverview accountOverview, String comparisonFund) {
    List<IndexPurchase> purchases = comparisonPurchases(accountOverview, comparisonFund);
    LocalDate endingBalanceDate = accountOverview.getEndingBalanceDate();
    double endingSameDayWeight =
        sameDayWeight(accountOverview.getEndingHoldings(), comparisonFund, endingBalanceDate);
    return pricesFor(comparisonFund, purchases, accountOverview.getStartDate(), endingBalanceDate)
        .map(
            prices ->
                purchases.stream()
                    .map(
                        purchase ->
                            purchase
                                .amount()
                                .divide(
                                    priceAt(prices, purchase.date(), purchase.sameDayWeight()),
                                    MathContext.DECIMAL128))
                    .reduce(ZERO, BigDecimal::add)
                    .multiply(priceAt(prices, endingBalanceDate, endingSameDayWeight)));
  }

  private record IndexPurchase(BigDecimal amount, LocalDate date, double sameDayWeight) {}

  private List<IndexPurchase> comparisonPurchases(
      AccountOverview accountOverview, String comparisonFund) {
    List<IndexPurchase> purchases = new ArrayList<>();
    BigDecimal beginningBalance = accountOverview.getBeginningBalance();
    if (beginningBalance.compareTo(ZERO) != 0) {
      LocalDate dayBeforeStart = accountOverview.getStartDate().minusDays(1);
      purchases.add(
          new IndexPurchase(
              beginningBalance,
              dayBeforeStart,
              sameDayWeight(
                  accountOverview.getBeginningHoldings(), comparisonFund, dayBeforeStart)));
    }
    accountOverview
        .getTransactions()
        .forEach(
            transaction ->
                purchases.add(
                    new IndexPurchase(
                        transaction.amount(),
                        transaction.date(),
                        sameDayWeight(transaction.isin(), comparisonFund, transaction.date()))));
    return purchases;
  }

  private double sameDayWeight(
      Map<String, BigDecimal> holdings, String comparisonFund, LocalDate date) {
    var heldFunds =
        holdings.entrySet().stream().filter(holding -> holding.getValue().signum() > 0).toList();
    BigDecimal total = heldFunds.stream().map(Map.Entry::getValue).reduce(ZERO, BigDecimal::add);
    if (!pricedAtUsClose(comparisonFund) || total.signum() == 0) {
      return FundPriceTiming.SAME_DAY;
    }
    return heldFunds.stream()
            .mapToDouble(
                holding ->
                    holding.getValue().doubleValue()
                        * sameDayWeight(holding.getKey(), comparisonFund, date))
            .sum()
        / total.doubleValue();
  }

  private double sameDayWeight(@Nullable String isin, String comparisonFund, LocalDate date) {
    if (isin == null || !pricedAtUsClose(comparisonFund)) {
      return FundPriceTiming.SAME_DAY;
    }
    Double estimated = fundPriceTiming.sameDayWeight(isin, comparisonFund, YearMonth.from(date));
    return estimated == null ? FundPriceTiming.SAME_DAY : estimated;
  }

  private static boolean pricedAtUsClose(String comparisonFund) {
    return WORLD_INDEXES_PRICED_AT_US_CLOSE.contains(comparisonFund);
  }

  private Optional<NavigableMap<LocalDate, BigDecimal>> pricesFor(
      String fund,
      List<IndexPurchase> purchases,
      LocalDate firstDayOfPeriod,
      LocalDate endingBalanceDate) {
    List<LocalDate> dates =
        Stream.concat(purchases.stream().map(IndexPurchase::date), Stream.of(endingBalanceDate))
            .toList();
    LocalDate earliest = Collections.min(dates);
    LocalDate loadFrom =
        pricedAtUsClose(fund)
            ? earliest.minusDays(CALENDAR_DAYS_BEFORE_THE_FIRST_PURCHASE_FOR_ITS_PREVIOUS_CLOSE)
            : earliest;
    return startingValue(fund, earliest, firstDayOfPeriod)
        .map(
            startingValue -> {
              NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
              LocalDate startingDate =
                  startingValue.date().isAfter(earliest) ? earliest : startingValue.date();
              prices.put(startingDate, startingValue.value());
              fundValueProvider
                  .findValuesBetweenDates(fund, loadFrom, Collections.max(dates))
                  .forEach(value -> prices.put(value.date(), value.value()));
              return pricedAtUsClose(fund)
                  ? withoutWeekendRowsRepeatingFridaysClose(prices, earliest)
                  : prices;
            });
  }

  private static NavigableMap<LocalDate, BigDecimal> withoutWeekendRowsRepeatingFridaysClose(
      NavigableMap<LocalDate, BigDecimal> prices, LocalDate earliest) {
    NavigableMap<LocalDate, BigDecimal> tradingCloses = new TreeMap<>(prices);
    tradingCloses
        .keySet()
        .removeIf(date -> date.getDayOfWeek() == SATURDAY || date.getDayOfWeek() == SUNDAY);
    return tradingCloses.floorKey(earliest) == null ? prices : tradingCloses;
  }

  private Optional<FundValue> startingValue(
      String fund, LocalDate earliestPurchaseDate, LocalDate firstDayOfPeriod) {
    return fundValueProvider
        .getLatestValue(fund, earliestPurchaseDate)
        .or(() -> fundValueProvider.getLatestValue(fund, firstDayOfPeriod));
  }

  private static BigDecimal priceAt(
      NavigableMap<LocalDate, BigDecimal> prices, LocalDate date, double sameDayWeight) {
    Map.Entry<LocalDate, BigDecimal> close = Objects.requireNonNull(prices.floorEntry(date));
    Map.Entry<LocalDate, BigDecimal> previousClose = prices.lowerEntry(close.getKey());
    if (sameDayWeight >= FundPriceTiming.SAME_DAY || previousClose == null) {
      return close.getValue();
    }
    return BigDecimal.valueOf(
        Math.exp(
            sameDayWeight * Math.log(close.getValue().doubleValue())
                + (1 - sameDayWeight) * Math.log(previousClose.getValue().doubleValue())));
  }

  private record CashReturn(BigDecimal paymentsSum, BigDecimal value) {
    public CashReturn() {
      this(ZERO, ZERO);
    }
  }

  private CashReturn getPersonalCashReturn(AccountOverview accountOverview) {
    final var paymentsSum =
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

  private CashReturn getSimulatedCashReturn(
      AccountOverview accountOverview, BigDecimal endingBalance) {
    final var paymentsSum =
        accountOverview.getTransactions().stream()
            .map(Transaction::amount)
            .reduce(BigDecimal::add)
            .orElse(ZERO);

    BigDecimal cashReturn =
        endingBalance
            .subtract(accountOverview.getBeginningBalance())
            .subtract(paymentsSum)
            .setScale(CASH_DECIMAL_PLACES, RoundingMode.HALF_UP);

    return new CashReturn(paymentsSum, cashReturn);
  }

  private List<Transaction> getPurchaseTransactions(AccountOverview accountOverview) {
    List<Transaction> purchaseTransactions = new ArrayList<>();

    BigDecimal beginningBalance = accountOverview.getBeginningBalance();
    if (beginningBalance.compareTo(ZERO) != 0) {
      purchaseTransactions.add(
          new Transaction(beginningBalance, accountOverview.getBeginningBalanceTime()));
    }
    purchaseTransactions.addAll(accountOverview.getTransactions());

    return purchaseTransactions;
  }

  private List<Transaction> negateTransactionAmounts(List<Transaction> transactions) {
    return transactions.stream().map(ReturnCalculator::negateTransactionAmount).toList();
  }

  private static Transaction negateTransactionAmount(Transaction transaction) {
    return new Transaction(transaction.amount().negate(), transaction.time());
  }

  @Nullable
  private BigDecimal calculateReturn(
      List<Transaction> purchaseTransactions, BigDecimal endingBalance, Instant endingBalanceTime) {
    List<Transaction> negatedTransactions = negateTransactionAmounts(purchaseTransactions);
    Transaction endingTransaction = new Transaction(endingBalance, endingBalanceTime);

    List<Transaction> internalTransactions = new ArrayList<>(negatedTransactions);
    internalTransactions.add(endingTransaction);

    return calculateInternalRateOfReturn(internalTransactions);
  }

  @Nullable
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
    } catch (IllegalArgumentException | ArithmeticException e) {
      log.warn(
          "XIRR solver found no rate: transactionCount={}, reason={}, message={}",
          transactions.size(),
          e.getClass().getSimpleName(),
          e.getMessage());
      return null;
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
