package ee.tuleva.onboarding.fund.fees;

import static ee.tuleva.onboarding.fund.fees.FundFeeUpdater.FeeField.ONGOING_CHARGES;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.fund.PublishedFigure;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SavingsFundOngoingChargesSync {

  private static final int FUND_TABLE_ONGOING_CHARGES_SCALE = 8;

  private final OngoingChargesFigures ongoingChargesFigures;
  private final FundRepository fundRepository;
  private final FundFeeUpdater fundFeeUpdater;

  void syncToTheKidsInForceOn(LocalDate date) {
    TulevaFund.getSavingsFunds().forEach(fund -> syncWithoutStoppingTheOthers(fund, date));
  }

  private void syncWithoutStoppingTheOthers(TulevaFund fund, LocalDate date) {
    try {
      sync(fund, date);
    } catch (Exception e) {
      log.error("Failed to sync ongoing charges figure: fund={}, date={}", fund, date, e);
    }
  }

  private void sync(TulevaFund fund, LocalDate date) {
    fundRepository
        .findUncachedByIsin(fund.getIsin())
        .ifPresentOrElse(
            row -> syncToTheKidInForce(row, fund, date),
            () ->
                log.error(
                    "Fund not found in fund table, skipping its ongoing charges figure: fund={}, isin={}",
                    fund,
                    fund.getIsin()));
  }

  private void syncToTheKidInForce(Fund row, TulevaFund fund, LocalDate date) {
    ongoingChargesFigures
        .figureInForceOn(fund, date)
        .ifPresentOrElse(
            kid -> fundFeeUpdater.applyUpdate(row, atFundTableScale(kid), ONGOING_CHARGES),
            () ->
                log.error(
                    "No published ongoing charges figure in force, keeping the fund table figure: fund={}, date={}",
                    fund,
                    date));
  }

  private static BigDecimal atFundTableScale(PublishedFigure kid) {
    return kid.figure().setScale(FUND_TABLE_ONGOING_CHARGES_SCALE, HALF_UP);
  }
}
