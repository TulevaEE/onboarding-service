package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.UnionStockIndexRetriever;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
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

  private final FundValueProvider fundValueProvider;
  private final PublicHolidays publicHolidays;

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
    List<Transaction> purchases =
        withFirstPurchaseOn(
            getComparisonPurchases(accountOverview),
            beginningBalanceIndexDate(accountOverview, comparisonFund));
    LocalDate endingBalanceDate = accountOverview.getEndingBalanceDate();
    return pricesFor(comparisonFund, purchases, endingBalanceDate)
        .map(
            prices ->
                purchases.stream()
                    .map(
                        purchase ->
                            purchase
                                .amount()
                                .divide(
                                    priceOnOrBefore(prices, purchase.date()),
                                    MathContext.DECIMAL128))
                    .reduce(ZERO, BigDecimal::add)
                    .multiply(priceOnOrBefore(prices, endingBalanceDate)));
  }

  private static List<Transaction> withFirstPurchaseOn(
      List<Transaction> purchases, @Nullable LocalDate firstPurchaseDate) {
    if (firstPurchaseDate == null || purchases.isEmpty()) {
      return purchases;
    }
    Transaction movedFirstPurchase =
        new Transaction(
            purchases.getFirst().amount(), firstPurchaseDate.atStartOfDay(UTC).toInstant());
    return Stream.concat(Stream.of(movedFirstPurchase), purchases.stream().skip(1)).toList();
  }

  private Optional<NavigableMap<LocalDate, BigDecimal>> pricesFor(
      String fund, List<Transaction> purchases, LocalDate endingBalanceDate) {
    List<LocalDate> dates =
        Stream.concat(purchases.stream().map(Transaction::date), Stream.of(endingBalanceDate))
            .toList();
    LocalDate earliest = Collections.min(dates);
    return fundValueProvider
        .getLatestValue(fund, earliest)
        .map(
            startingValue -> {
              NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
              prices.put(earliest, startingValue.value());
              fundValueProvider
                  .findValuesBetweenDates(fund, earliest, Collections.max(dates))
                  .forEach(value -> prices.put(value.date(), value.value()));
              return prices;
            });
  }

  private static BigDecimal priceOnOrBefore(
      NavigableMap<LocalDate, BigDecimal> prices, LocalDate date) {
    return Objects.requireNonNull(prices.floorEntry(date)).getValue();
  }

  /**
   * When the comparison period starts on a weekend/holiday, aligns the index lookup date with the
   * fund's NAV timing to prevent inflated simulated returns.
   *
   * <p>Fund NAVs (from EPIS/Pensionikeskus) are priced at European market close (~17:00 CET), while
   * the comparison index (UNION_STOCK_INDEX, sourced from Morningstar) is priced at US market close
   * (~22:00 CET). On normal days the 5-hour gap is negligible, but during crashes it can cause a
   * significant bias:
   *
   * <p>Example: "Last year" starting Monday Apr 7, 2025 (during the tariff crash):
   *
   * <ul>
   *   <li>Synthetic beginning balance transaction lands on Sunday Apr 6
   *   <li>Index lookup falls back to Friday Apr 4 US close (385.62), because of weekend
   *       carry-forward
   *   <li>EPIS beginning balance uses TUK75 Friday NAV (European close, 17:00, value 1.13907)
   *   <li>The 5-hour gap (European close → US close) saw a ~4% further decline on the crash day
   *   <li>Higher beginning balance / lower index price = ~4% excess virtual units → ~5pp inflated
   *       XIRR
   * </ul>
   *
   * <p>Fix: when the synthetic transaction date (startTime - 1 day) falls on a non-working day, the
   * index lookup crosses a weekend/holiday boundary to an earlier business day. We shift one more
   * business day back to align: Thursday US close (~22:00) is the last full global market close
   * BEFORE Friday's European close (~17:00), where the fund NAV is set.
   *
   * <p>Only applies to UNION_STOCK_INDEX (sourced from Morningstar, US close). Other comparison
   * targets (EPI, CPI, individual fund ISINs) use European-close pricing and don't need adjustment.
   *
   * @return adjusted lookup date, or null to use the default (transaction date)
   */
  private @Nullable LocalDate beginningBalanceIndexDate(
      AccountOverview accountOverview, String comparisonFund) {
    if (!UnionStockIndexRetriever.KEY.equals(comparisonFund)) {
      return null;
    }
    if (accountOverview.getBeginningBalance().compareTo(ZERO) == 0) {
      return null;
    }
    LocalDate syntheticDate =
        accountOverview.getStartTime().minus(1, DAYS).atOffset(UTC).toLocalDate();
    if (publicHolidays.isWorkingDay(syntheticDate)) {
      return null;
    }
    LocalDate effectiveDate = publicHolidays.previousWorkingDay(syntheticDate);
    return publicHolidays.previousWorkingDay(effectiveDate);
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
    return purchasesWithBeginningBalanceAt(
        accountOverview, accountOverview.getBeginningBalanceTime());
  }

  private List<Transaction> getComparisonPurchases(AccountOverview accountOverview) {
    return purchasesWithBeginningBalanceAt(
        accountOverview, accountOverview.getStartTime().minus(1, DAYS));
  }

  private List<Transaction> purchasesWithBeginningBalanceAt(
      AccountOverview accountOverview, Instant beginningBalanceTime) {
    List<Transaction> purchaseTransactions = new ArrayList<>();

    BigDecimal beginningBalance = accountOverview.getBeginningBalance();
    if (beginningBalance.compareTo(ZERO) != 0) {
      purchaseTransactions.add(new Transaction(beginningBalance, beginningBalanceTime));
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
