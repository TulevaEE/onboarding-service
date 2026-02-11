package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  private static final Map<FeeType, SystemAccount> FEE_TYPE_ACCOUNTS =
      Map.of(
          MANAGEMENT, MANAGEMENT_FEE_ACCRUAL,
          DEPOT, DEPOT_FEE_ACCRUAL);

  private final List<FeeCalculator> feeCalculators;
  private final FeeAccrualRepository feeAccrualRepository;
  private final NavFeeAccrualLedger navFeeAccrualLedger;

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
      BigDecimal amount = roundForLedger(accrual.dailyAmountNet());
      SystemAccount feeAccount = FEE_TYPE_ACCOUNTS.get(accrual.feeType());
      navFeeAccrualLedger.recordFeeAccrual(fund.name(), date, feeAccount, amount);
    }
    log.debug("Recorded fee accruals to ledger: fund={}, date={}", fund, date);
  }

  private BigDecimal roundForLedger(BigDecimal amount) {
    return amount != null ? amount.setScale(2, HALF_UP) : null;
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
