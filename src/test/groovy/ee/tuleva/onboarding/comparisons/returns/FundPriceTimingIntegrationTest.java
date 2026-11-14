package ee.tuleva.onboarding.comparisons.returns;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.time.ClockConfig;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JdbcFundValueRepository.class, FundPriceTiming.class, ClockConfig.class})
class FundPriceTimingIntegrationTest {

  @Autowired JdbcFundValueRepository fundValueRepository;
  @Autowired FundPriceTiming fundPriceTiming;

  @Test
  void findsFromStoredPricesThatAFundFollowsTheIndexADayLateAndAnotherOnTheSameDay() {
    var tradingDays =
        LocalDate.of(2025, 1, 1)
            .datesUntil(LocalDate.of(2025, 8, 1))
            .filter(day -> day.getDayOfWeek() != SATURDAY && day.getDayOfWeek() != SUNDAY)
            .toList();
    var indexMoves = new Random(42).doubles(tradingDays.size(), -0.02, 0.02).toArray();
    var dayLateMoves = new double[tradingDays.size()];
    for (var day = 1; day < tradingDays.size(); day++) {
      dayLateMoves[day] = indexMoves[day - 1];
    }
    var rows = new ArrayList<FundValue>();
    rows.addAll(levels("MSCI_ACWI_ADJUSTED", tradingDays, indexMoves));
    rows.addAll(levels("EE0000000001", tradingDays, indexMoves));
    rows.addAll(levels("EE0000000002", tradingDays, dayLateMoves));
    fundValueRepository.saveAll(rows);

    var august = YearMonth.of(2025, 8);
    assertThat(fundPriceTiming.sameDayWeight("EE0000000001", "MSCI_ACWI_ADJUSTED", august))
        .isCloseTo(1.0, within(0.02));
    assertThat(fundPriceTiming.sameDayWeight("EE0000000002", "MSCI_ACWI_ADJUSTED", august))
        .isCloseTo(0.0, within(0.02));
  }

  private static List<FundValue> levels(String key, List<LocalDate> days, double[] moves) {
    var values = new ArrayList<FundValue>();
    var level = 100.0;
    for (var day = 0; day < days.size(); day++) {
      level *= Math.exp(moves[day]);
      values.add(aFundValue(key, days.get(day), Math.round(level * 100_000) / 100_000.0));
    }
    return values;
  }
}
