package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private static final Map<FeeType, SystemAccount> FEE_TYPE_ACCOUNTS =
      Map.of(
          MANAGEMENT, MANAGEMENT_FEE_ACCRUAL,
          DEPOT, DEPOT_FEE_ACCRUAL);

  private final List<FeeCalculator> feeCalculators;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;
  private final NavLedgerRepository navLedgerRepository;
  private final FeeMonthResolver feeMonthResolver;

  @Transactional
  public void calculateDailyFees(LocalDate date) {
    for (TulevaFund fund : TulevaFund.values()) {
      calculateDailyFeesForFund(fund, date);
    }
  }

  @Transactional
  public void calculateDailyFeesForFund(TulevaFund fund, LocalDate date) {
    log.debug("Calculating fees: fund={}, date={}", fund, date);
    for (FeeCalculator calculator : feeCalculators) {
      FeeAccrual accrual = calculator.calculate(fund, date);
      feeAccrualRepository.save(accrual);
      if (fund.hasNavCalculation()) {
        SystemAccount feeAccount = FEE_TYPE_ACCOUNTS.get(accrual.feeType());
        BigDecimal ledgerAmount = roundForLedger(accrual.dailyAmountNet());
        Map<String, Object> metadata = buildAccrualMetadata(accrual, feeAccount, ledgerAmount);
        navFeeAccrualLedger.recordFeeAccrual(fund.name(), date, feeAccount, ledgerAmount, metadata);
      }
    }
    if (fund.hasNavCalculation()) {
      LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(date);
      LocalDate previousMonth = feeMonth.minusMonths(1);
      settleMonthlyFeesIfNeeded(fund, previousMonth);
    }
    log.debug("Recorded fee accruals: fund={}, date={}", fund, date);
  }

  private void settleMonthlyFeesIfNeeded(TulevaFund fund, LocalDate month) {
    Instant cutoff = month.plusMonths(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    LocalDate settlementDate = month.plusMonths(1).minusDays(1);

    FEE_TYPE_ACCOUNTS.forEach(
        (feeType, feeAccount) -> {
          BigDecimal balance =
              navLedgerRepository.getSystemAccountBalanceBefore(
                  feeAccount.getAccountName(), cutoff);
          BigDecimal settlementAmount = balance.negate();
          if (settlementAmount.signum() > 0) {
            navFeeAccrualLedger.settleFeeAccrual(
                fund.name(), settlementDate, feeAccount, settlementAmount);
          }
        });
  }

  private BigDecimal roundForLedger(BigDecimal amount) {
    return amount != null ? amount.setScale(2, HALF_UP) : null;
  }

  private Map<String, Object> buildAccrualMetadata(
      FeeAccrual accrual, SystemAccount feeAccount, BigDecimal ledgerAmount) {
    var metadata = new HashMap<String, Object>();
    metadata.put("operationType", "FEE_ACCRUAL");
    metadata.put("fund", accrual.fund().name());
    metadata.put("feeType", feeAccount.name());
    metadata.put("accrualDate", accrual.accrualDate());
    metadata.put("baseValue", accrual.baseValue());
    metadata.put("annualRate", accrual.annualRate());
    metadata.put("daysInYear", accrual.daysInYear());
    metadata.put("referenceDate", accrual.referenceDate());
    metadata.put("feeMonth", accrual.feeMonth());
    metadata.put("dailyAmountNet", accrual.dailyAmountNet());
    if (accrual.vatRate() != null) {
      metadata.put("vatRate", accrual.vatRate());
      metadata.put("dailyAmountGross", accrual.dailyAmountGross());
    }
    metadata.put("ledgerAmount", ledgerAmount);
    return metadata;
  }

  @Transactional
  public void backfillFees(LocalDate startDate, LocalDate endDate) {
    log.info("Starting fee backfill: startDate={}, endDate={}", startDate, endDate);

    LocalDate current = startDate;
    while (!current.isAfter(endDate)) {
      calculateDailyFees(current);
      current = current.plusDays(1);
    }

    log.info("Completed fee backfill: startDate={}, endDate={}", startDate, endDate);
  }
}
