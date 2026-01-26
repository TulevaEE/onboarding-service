package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

  private final List<FeeCalculator> feeCalculators;
  private final FeeAccrualRepository feeAccrualRepository;

  @Transactional
  public void calculateDailyFees(LocalDate date) {
    for (TulevaFund fund : TulevaFund.values()) {
      calculateDailyFeesForFund(fund, date);
    }
  }

  @Transactional
  public void calculateDailyFeesForFund(TulevaFund fund, LocalDate date) {
    log.debug("Calculating fees: fund={}, date={}", fund, date);
    feeCalculators.forEach(
        calculator -> feeAccrualRepository.save(calculator.calculate(fund, date)));
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
