package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculation;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationService;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NavLedgerReconciliation {

  private final NavLedgerRepository navLedgerRepository;
  private final PositionCalculationService positionCalculationService;
  private final FundPositionRepository fundPositionRepository;
  private final FeeAccrualRepository feeAccrualRepository;

  public ReconciliationResult reconcile(TulevaFund fund, LocalDate date) {
    List<Discrepancy> discrepancies =
        Stream.of(
                compareSecuritiesValue(fund, date),
                compareCashPosition(fund, date),
                compareReceivables(fund, date),
                comparePayables(fund, date),
                compareManagementFeeAccrual(fund, date),
                compareDepotFeeAccrual(fund, date))
            .flatMap(Optional::stream)
            .toList();

    return new ReconciliationResult(fund, date, discrepancies);
  }

  private Optional<Discrepancy> compareSecuritiesValue(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(SECURITIES_VALUE);
    BigDecimal externalValue =
        positionCalculationService.calculate(fund, date).stream()
            .map(PositionCalculation::calculatedMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);

    return createDiscrepancyIfDifferent("securities_value", ledgerValue, externalValue);
  }

  private Optional<Discrepancy> compareCashPosition(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(CASH_POSITION);
    BigDecimal externalValue = getPositionValue(fund, date, CASH);

    return createDiscrepancyIfDifferent("cash_position", ledgerValue, externalValue);
  }

  private Optional<Discrepancy> compareReceivables(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(TRADE_RECEIVABLES);
    BigDecimal externalValue = getPositionValue(fund, date, RECEIVABLES);

    return createDiscrepancyIfDifferent("receivables", ledgerValue, externalValue);
  }

  private Optional<Discrepancy> comparePayables(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(TRADE_PAYABLES);
    BigDecimal externalValue = getPositionValue(fund, date, LIABILITY);

    return createDiscrepancyIfDifferent("payables", ledgerValue, externalValue);
  }

  private Optional<Discrepancy> compareManagementFeeAccrual(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(MANAGEMENT_FEE_ACCRUAL);
    BigDecimal externalValue = getFeeAccrualValue(fund, date, MANAGEMENT);

    return createDiscrepancyIfDifferent(
        "management_fee_accrual", ledgerValue, externalValue.negate());
  }

  private Optional<Discrepancy> compareDepotFeeAccrual(TulevaFund fund, LocalDate date) {
    BigDecimal ledgerValue = getLedgerBalance(DEPOT_FEE_ACCRUAL);
    BigDecimal externalValue = getFeeAccrualValue(fund, date, DEPOT);

    return createDiscrepancyIfDifferent("depot_fee_accrual", ledgerValue, externalValue.negate());
  }

  private BigDecimal getLedgerBalance(SystemAccount account) {
    BigDecimal balance = navLedgerRepository.getSystemAccountBalance(account.getAccountName());
    return balance != null ? balance : ZERO;
  }

  private BigDecimal getPositionValue(TulevaFund fund, LocalDate date, AccountType accountType) {
    return fundPositionRepository
        .findByReportingDateAndFundAndAccountType(date, fund, accountType)
        .stream()
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal getFeeAccrualValue(TulevaFund fund, LocalDate date, FeeType feeType) {
    return feeAccrualRepository
        .findByFundAndAccrualDateAndFeeType(fund, date, feeType)
        .map(FeeAccrual::dailyAmountNet)
        .orElse(ZERO);
  }

  private Optional<Discrepancy> createDiscrepancyIfDifferent(
      String component, BigDecimal ledgerValue, BigDecimal externalValue) {
    if (ledgerValue.compareTo(externalValue) != 0) {
      return Optional.of(new Discrepancy(component, ledgerValue, externalValue));
    }
    return Optional.empty();
  }

  public record ReconciliationResult(
      TulevaFund fund, LocalDate date, List<Discrepancy> discrepancies) {

    public boolean isReconciled() {
      return discrepancies.isEmpty();
    }
  }

  public record Discrepancy(String component, BigDecimal ledgerValue, BigDecimal externalValue) {

    public BigDecimal difference() {
      return ledgerValue.subtract(externalValue);
    }
  }
}
