package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.NavPositionLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundPositionLedgerService {

  private final FundPositionRepository fundPositionRepository;
  private final NavPositionLedger navPositionLedger;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;

  @Transactional
  public void rerecordPositionsFromDate(TulevaFund fund, LocalDate fromDate) {
    log.info("Re-recording positions from date: fund={}, fromDate={}", fund, fromDate);

    LocalDate lastWorkingDayBefore = publicHolidays.previousWorkingDay(fromDate);
    List<LocalDate> datesToRerecord =
        fundPositionRepository.findDistinctNavDatesByFund(fund).stream()
            .filter(date -> !date.isBefore(lastWorkingDayBefore))
            .toList();

    navPositionLedger.deletePositionUpdatesForDates(fund, datesToRerecord);

    for (LocalDate date : datesToRerecord) {
      recordPositionsToLedger(fund, date);
    }

    log.info(
        "Re-recorded positions from date: fund={}, fromDate={}, dates={}",
        fund,
        fromDate,
        datesToRerecord.size());
  }

  @Transactional
  public void rerecordPositions(TulevaFund fund, LocalDate fromDate) {
    log.info("Re-recording positions and fees: fund={}, fromDate={}", fund, fromDate);

    navPositionLedger.deletePositionUpdatesByFund(fund);
    navFeeAccrualLedger.deleteFeeTransactionsByFund(fund);
    feeAccrualRepository.deleteByFund(fund);

    LocalDate lastWorkingDayBefore = publicHolidays.previousWorkingDay(fromDate);
    List<LocalDate> dates =
        fundPositionRepository.findDistinctNavDatesByFund(fund).stream()
            .filter(date -> !date.isBefore(lastWorkingDayBefore))
            .toList();

    for (LocalDate date : dates) {
      recordPositionsToLedger(fund, date);
    }

    log.info("Re-recorded positions and fees: fund={}, dates={}", fund, dates.size());
  }

  public void recordPositionsToLedger(TulevaFund fund, LocalDate date) {
    PositionDeltas deltas = calculatePositionDeltas(fund, date);

    log.info(
        "Recording position deltas to ledger: fund={}, date={}, securitiesUnitDeltas={}, cash={}, receivables={}, payables={}",
        fund,
        date,
        deltas.securitiesUnits,
        deltas.cash,
        deltas.receivables,
        deltas.payables);
    navPositionLedger.recordPositions(
        fund, date, deltas.securitiesUnits, deltas.cash, deltas.receivables, deltas.payables);
  }

  private PositionDeltas calculatePositionDeltas(TulevaFund fund, LocalDate date) {
    return new PositionDeltas(
        calculateSecuritiesUnitDeltas(fund, date),
        calculateDelta(CASH_POSITION, fund, calculatePositionValue(fund, date, CASH)),
        calculateDelta(TRADE_RECEIVABLES, fund, calculateTradeReceivables(fund, date)),
        calculateDelta(TRADE_PAYABLES, fund, calculateTradePayables(fund, date)));
  }

  private record PositionDeltas(
      Map<String, BigDecimal> securitiesUnits,
      BigDecimal cash,
      BigDecimal receivables,
      BigDecimal payables) {}

  private Map<String, BigDecimal> calculateSecuritiesUnitDeltas(TulevaFund fund, LocalDate date) {
    List<FundPosition> securityPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY);

    Map<String, BigDecimal> deltas = new HashMap<>();
    Set<String> reportedIsins = new HashSet<>();

    for (FundPosition position : securityPositions) {
      String isin = position.getAccountId();
      if (isin == null) {
        continue;
      }
      reportedIsins.add(isin);
      BigDecimal newQuantity = position.getQuantity() != null ? position.getQuantity() : ZERO;
      BigDecimal currentBalance =
          navLedgerRepository.getSystemAccountBalance(SECURITIES_UNITS.getAccountName(fund, isin));
      BigDecimal delta = newQuantity.subtract(currentBalance);
      if (delta.signum() != 0) {
        deltas.put(isin, delta);
      }
    }

    navLedgerRepository
        .getSecuritiesUnitBalances(fund)
        .forEach(
            (isin, balance) -> {
              if (!reportedIsins.contains(isin) && balance.signum() != 0) {
                deltas.put(isin, balance.negate());
              }
            });

    return deltas;
  }

  private BigDecimal calculateDelta(SystemAccount account, TulevaFund fund, BigDecimal newValue) {
    BigDecimal currentBalance =
        navLedgerRepository.getSystemAccountBalance(account.getAccountName(fund));
    return newValue.subtract(currentBalance);
  }

  private BigDecimal calculateTradeReceivables(TulevaFund fund, LocalDate date) {
    BigDecimal totalReceivables = calculatePositionValue(fund, date, RECEIVABLES);
    BigDecimal pendingSubscriptions = getPendingSubscriptions(fund, date);
    BigDecimal tradeReceivables = totalReceivables.subtract(pendingSubscriptions);

    if (tradeReceivables.signum() < 0) {
      log.error(
          "Trade receivables is negative after subtracting pending subscriptions:"
              + " fund={}, date={}, totalReceivables={}, pendingSubscriptions={}, tradeReceivables={}",
          fund,
          date,
          totalReceivables,
          pendingSubscriptions,
          tradeReceivables);
    }

    return tradeReceivables;
  }

  private BigDecimal getPendingSubscriptions(TulevaFund fund, LocalDate date) {
    return fundPositionRepository
        .findByNavDateAndFundAndAccountTypeAndAccountId(date, fund, RECEIVABLES, fund.getIsin())
        .map(FundPosition::getMarketValue)
        .orElse(ZERO);
  }

  private BigDecimal calculateTradePayables(TulevaFund fund, LocalDate date) {
    return fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, LIABILITY).stream()
        .filter(p -> isTradePayable(p.getAccountName()))
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private boolean isTradePayable(String accountName) {
    return accountName != null
        && (accountName.contains("payables of unsettled transactions")
            || accountName.contains("Trade Settlement Payable"));
  }

  private BigDecimal calculatePositionValue(TulevaFund fund, LocalDate date, AccountType type) {
    return fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, type).stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
