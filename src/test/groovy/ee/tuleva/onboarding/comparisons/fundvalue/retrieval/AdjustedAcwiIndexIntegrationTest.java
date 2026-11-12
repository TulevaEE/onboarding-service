package ee.tuleva.onboarding.comparisons.fundvalue.retrieval;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.JdbcFundValueRepository;
import ee.tuleva.onboarding.time.ClockConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JdbcFundValueRepository.class, AdjustedAcwiIndexRetriever.class, ClockConfig.class})
class AdjustedAcwiIndexIntegrationTest {

  @Autowired JdbcFundValueRepository fundValueRepository;
  @Autowired AdjustedAcwiIndexRetriever retriever;

  @Test
  void storesAWorldIndexHeldAtWhatTheIiPillarLawAllowedInStocksFromTwoThousandThree() {
    fundValueRepository.saveAll(
        List.of(
            value("MSCI_ACWI", "2003-01-07", 100),
            value("MSCI_ACWI", "2008-11-13", 90),
            value("MSCI_ACWI", "2008-12-31", 80),
            value("MSCI_ACWI", "2016-05-31", 160),
            value("MSCI_ACWI", "2018-12-31", 180),
            value("MSCI_ACWI", "2019-09-02", 200),
            value("MSCI_ACWI", "2019-12-31", 220),
            value("EURO_GOVERNMENT_BOND", "2003-01-07", 50),
            value("EURO_GOVERNMENT_BOND", "2008-11-13", 63),
            value("EURO_GOVERNMENT_BOND", "2008-12-31", 64),
            value("EURO_GOVERNMENT_BOND", "2016-05-31", 90),
            value("EURO_AGGREGATE_BOND", "2016-06-01", 120),
            value("EURO_AGGREGATE_BOND", "2018-12-31", 118),
            value("EURO_AGGREGATE_BOND", "2019-09-02", 130),
            value("EURO_AGGREGATE_BOND", "2019-09-09", 131)));

    fundValueRepository.saveAll(
        retriever.retrieveValuesForRange(
            LocalDate.parse("2003-01-07"), LocalDate.parse("2026-11-12")));

    var stored =
        fundValueRepository.findValuesBetweenDates(
            "MSCI_ACWI_ADJUSTED", LocalDate.parse("2003-01-07"), LocalDate.parse("2026-11-12"));
    assertThat(stored).extracting(FundValue::date).hasSize(7);
    assertThat(growth(stored, "2003-01-07", "2008-11-13"))
        .isCloseTo(0.5 * 90 / 100 + 0.5 * 63 / 50, within(1e-5));
    assertThat(growth(stored, "2008-11-13", "2008-12-31"))
        .isCloseTo(0.75 * 80 / 90 + 0.25 * 64 / 63, within(1e-5));
    assertThat(growth(stored, "2018-12-31", "2019-12-31"))
        .isCloseTo((0.75 * 200 / 180 + 0.25 * 130 / 118) * 220 / 200, within(1e-5));
  }

  private static double growth(List<FundValue> series, String from, String to) {
    return valueOn(series, to) / valueOn(series, from);
  }

  private static double valueOn(List<FundValue> series, String day) {
    return series.stream()
        .filter(value -> value.date().equals(LocalDate.parse(day)))
        .findFirst()
        .orElseThrow()
        .value()
        .doubleValue();
  }

  private static FundValue value(String key, String day, double value) {
    return aFundValue(key, LocalDate.parse(day), value);
  }
}
