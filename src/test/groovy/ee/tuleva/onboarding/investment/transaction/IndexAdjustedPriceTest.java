package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.BLACKROCK;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.PriceValuation.Kind.POSITION_DATE_PRICE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexAdjustedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexMove;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IndexAdjustedPriceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 23);
  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 11, 24);

  private final IndexAdjustedPrice moved =
      new IndexAdjustedPrice(
          nav(NAV_DATE, "13.41703"),
          POSITION_DATE,
          new IndexMove(
              "MSCI_EM_EX_SELECT_CONTROVERSIES",
              new BigDecimal("2304.4413696217584"),
              new BigDecimal("2370.6908866778217")),
          new BigDecimal("13.802751"));

  private final IndexAdjustedPrice publishedOnThePositionDate =
      new IndexAdjustedPrice(
          nav(POSITION_DATE, "13.80"), POSITION_DATE, null, new BigDecimal("13.80"));

  @Test
  void aMovedPriceShowsTheFundsOwnPriceAndBothIndexLevelsItWasMovedBy() {
    assertThat(moved.resolution("IE00BKPTWY98"))
        .isEqualTo(
            Map.of(
                "isin", "IE00BKPTWY98",
                "price", "13.802751",
                "priceDate", "2026-11-24",
                "priceSource", "INDEX_ADJUSTED",
                "fundPrice", "13.41703",
                "fundPriceDate", "2026-11-23",
                "fundPriceValidationStatus", "OK",
                "index", "MSCI_EM_EX_SELECT_CONTROVERSIES",
                "indexLevelOnFundPriceDate", "2304.4413696217584",
                "indexLevelOnPositionDate", "2370.6908866778217"));
  }

  @Test
  void aNavPublishedForThePositionDateKeepsItsOwnSourceAndNeedsNoIndex() {
    assertThat(publishedOnThePositionDate.resolution("IE00BKPTWY98"))
        .isEqualTo(
            Map.of(
                "isin", "IE00BKPTWY98",
                "price", "13.80",
                "priceDate", "2026-11-24",
                "priceSource", "BLACKROCK",
                "fundPrice", "13.80",
                "fundPriceDate", "2026-11-24",
                "fundPriceValidationStatus", "OK"));
  }

  @Test
  void theOrderOutputNamesThePriceAndItsSource() {
    Map<String, Object> trade = new HashMap<>();

    moved.describe(trade);

    assertThat(trade).isEqualTo(Map.of("price", "13.802751", "priceSource", "INDEX_ADJUSTED"));
  }

  @Test
  void movedOrNotThePositionIsValuedAtThePriceOnThePositionDate() {
    assertThat(moved.revaluation())
        .contains(new RevaluationPrice(new BigDecimal("13.802751"), POSITION_DATE_PRICE));
    assertThat(publishedOnThePositionDate.revaluation())
        .contains(new RevaluationPrice(new BigDecimal("13.80"), POSITION_DATE_PRICE));
  }

  private static ResolvedPrice nav(LocalDate date, String value) {
    return ResolvedPrice.builder()
        .usedPrice(new BigDecimal(value))
        .priceDate(date)
        .priceSource(BLACKROCK)
        .validationStatus(OK)
        .build();
  }
}
