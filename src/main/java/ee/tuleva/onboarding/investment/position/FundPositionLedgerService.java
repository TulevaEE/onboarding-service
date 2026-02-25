package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FundPositionLedgerService {

  private final FundPositionRepository fundPositionRepository;
  private final NavPositionLedger navPositionLedger;
  private final NavLedgerRepository navLedgerRepository;

  public void recordPositionsToLedger(TulevaFund fund, LocalDate date) {
    Map<String, BigDecimal> securitiesUnitDeltas = calculateSecuritiesUnitDeltas(fund, date);
    BigDecimal cashDelta = calculateDelta(CASH_POSITION, calculatePositionValue(fund, date, CASH));
    BigDecimal receivablesDelta =
        calculateDelta(TRADE_RECEIVABLES, calculatePositionValue(fund, date, RECEIVABLES));
    BigDecimal payablesDelta =
        calculateDelta(TRADE_PAYABLES, calculatePositionValue(fund, date, LIABILITY));

    log.info(
        "Recording position deltas to ledger: fund={}, date={}, securitiesUnitDeltas={}, cash={}, receivables={}, payables={}",
        fund,
        date,
        securitiesUnitDeltas,
        cashDelta,
        receivablesDelta,
        payablesDelta);
    navPositionLedger.recordPositions(
        fund.name(), date, securitiesUnitDeltas, cashDelta, receivablesDelta, payablesDelta);
  }

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
          navLedgerRepository.getSystemAccountBalance(SECURITIES_UNITS.getAccountName(isin));
      BigDecimal delta = newQuantity.subtract(currentBalance);
      if (delta.signum() != 0) {
        deltas.put(isin, delta);
      }
    }

    navLedgerRepository
        .getSecuritiesUnitBalances()
        .forEach(
            (isin, balance) -> {
              if (!reportedIsins.contains(isin) && balance.signum() != 0) {
                deltas.put(isin, balance.negate());
              }
            });

    return deltas;
  }

  private BigDecimal calculateDelta(SystemAccount account, BigDecimal newValue) {
    BigDecimal currentBalance =
        navLedgerRepository.getSystemAccountBalance(account.getAccountName());
    return newValue.subtract(currentBalance);
  }

  private BigDecimal calculatePositionValue(TulevaFund fund, LocalDate date, AccountType type) {
    return fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, type).stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
