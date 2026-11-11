package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.MANAGEMENT_FEE;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.ManagementFeeRates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SavingsFundManagementFeeSync {

  private static final int FUND_TABLE_MANAGEMENT_FEE_SCALE = 7;

  private final ManagementFeeRates managementFeeRates;
  private final FundRepository fundRepository;
  private final FundFeeUpdater fundFeeUpdater;

  void syncToTheRatesInForceOn(LocalDate date) {
    TulevaFund.getSavingsFunds().forEach(fund -> syncWithoutStoppingTheOthers(fund, date));
  }

  private void syncWithoutStoppingTheOthers(TulevaFund fund, LocalDate date) {
    try {
      sync(fund, date);
    } catch (Exception e) {
      log.error("Failed to sync management fee: fund={}, date={}", fund, date, e);
    }
  }

  private void sync(TulevaFund fund, LocalDate date) {
    fundRepository
        .findUncachedByIsin(fund.getIsin())
        .ifPresentOrElse(
            row -> syncToTheRateInForce(row, fund, date),
            () ->
                log.error(
                    "Fund not found in fund table, skipping its management fee: fund={}, isin={}",
                    fund,
                    fund.getIsin()));
  }

  private void syncToTheRateInForce(Fund row, TulevaFund fund, LocalDate date) {
    managementFeeRates
        .rateInForceOn(fund, date)
        .ifPresentOrElse(
            rate -> fundFeeUpdater.applyUpdate(row, atFundTableScale(rate), MANAGEMENT_FEE),
            () ->
                log.error(
                    "No management fee rate in force, keeping the fund table rate: fund={}, date={}",
                    fund,
                    date));
  }

  private static BigDecimal atFundTableScale(BigDecimal rate) {
    return rate.setScale(FUND_TABLE_MANAGEMENT_FEE_SCALE, HALF_UP);
  }
}
