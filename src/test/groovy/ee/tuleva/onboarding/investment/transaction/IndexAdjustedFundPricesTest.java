package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.FundValueFixture.aFundValue;
import static ee.tuleva.onboarding.comparisons.fundvalue.PriceSource.BLACKROCK;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.IndexAdjustedFundPrices.Adjusted;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexAdjustedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexMove;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexAdjustedFundPricesTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 11, 23);
  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 11, 24);
  private static final String DEVELOPED_WORLD_FUND = "IE00BFG1TM61";
  private static final String EMERGING_MARKETS_FUND = "IE00BKPTWY98";

  @Mock private InstrumentReferenceService instrumentReferenceService;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private FundValueProvider fundValueProvider;

  @InjectMocks private IndexAdjustedFundPrices indexAdjustedFundPrices;

  @Test
  void aDevelopedWorldIndexFundsNavIsMovedToThePositionDateByMsciWorldScreened() {
    givenIndexFund(DEVELOPED_WORLD_FUND, "EQUITY_DM");
    givenNav(DEVELOPED_WORLD_FUND, NAV_DATE, "38.22237");
    givenLevel("MSCI_WORLD_SCREENED", NAV_DATE, "5988.346812253597");
    givenLevel("MSCI_WORLD_SCREENED", POSITION_DATE, "5933.218881157142");

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of(DEVELOPED_WORLD_FUND), POSITION_DATE))
        .isEqualTo(
            new Adjusted(
                Map.of(
                    DEVELOPED_WORLD_FUND,
                    new IndexAdjustedPrice(
                        nav(NAV_DATE, "38.22237"),
                        POSITION_DATE,
                        new IndexMove(
                            "MSCI_WORLD_SCREENED",
                            new BigDecimal("5988.346812253597"),
                            new BigDecimal("5933.218881157142")),
                        new BigDecimal("37.870500"))),
                List.of(),
                List.of()));
  }

  @Test
  void anEmergingMarketsIndexFundsNavIsMovedByMsciEmExSelectControversies() {
    givenIndexFund(EMERGING_MARKETS_FUND, "EQUITY_EM");
    givenNav(EMERGING_MARKETS_FUND, NAV_DATE, "13.41703");
    givenLevel("MSCI_EM_EX_SELECT_CONTROVERSIES", NAV_DATE, "2304.4413696217584");
    givenLevel("MSCI_EM_EX_SELECT_CONTROVERSIES", POSITION_DATE, "2370.6908866778217");

    assertThat(
            indexAdjustedFundPrices
                .onPositionDate(List.of(EMERGING_MARKETS_FUND), POSITION_DATE)
                .prices()
                .get(EMERGING_MARKETS_FUND)
                .value())
        .isEqualTo(new BigDecimal("13.802751"));
  }

  @Test
  void aNavAlreadyPublishedForThePositionDateIsUsedAsItIsWithoutAnIndex() {
    givenIndexFund(DEVELOPED_WORLD_FUND, "EQUITY_DM");
    givenNav(DEVELOPED_WORLD_FUND, POSITION_DATE, "37.87");

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of(DEVELOPED_WORLD_FUND), POSITION_DATE))
        .isEqualTo(
            new Adjusted(
                Map.of(
                    DEVELOPED_WORLD_FUND,
                    new IndexAdjustedPrice(
                        nav(POSITION_DATE, "37.87"), POSITION_DATE, null, new BigDecimal("37.87"))),
                List.of(),
                List.of()));
  }

  @Test
  void aMissingIndexLevelIsListedAndTheFundIsNotPriced() {
    givenIndexFund(EMERGING_MARKETS_FUND, "EQUITY_EM");
    givenNav(EMERGING_MARKETS_FUND, NAV_DATE, "13.41703");
    givenLevel("MSCI_EM_EX_SELECT_CONTROVERSIES", NAV_DATE, "2304.4413696217584");
    given(fundValueProvider.getValueForDate("MSCI_EM_EX_SELECT_CONTROVERSIES", POSITION_DATE))
        .willReturn(Optional.empty());

    assertThat(
            indexAdjustedFundPrices.onPositionDate(List.of(EMERGING_MARKETS_FUND), POSITION_DATE))
        .isEqualTo(
            new Adjusted(
                Map.of(),
                List.of(
                    new MissingIndexLevel(
                        EMERGING_MARKETS_FUND, "MSCI_EM_EX_SELECT_CONTROVERSIES", POSITION_DATE)),
                List.of()));
  }

  @Test
  void anIndexFundWithNoNavOnOrBeforeThePositionDateIsListedAndNotPriced() {
    givenIndexFund(DEVELOPED_WORLD_FUND, "EQUITY_DM");
    given(positionPriceResolver.resolve(DEVELOPED_WORLD_FUND, POSITION_DATE))
        .willReturn(Optional.empty());

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of(DEVELOPED_WORLD_FUND), POSITION_DATE))
        .isEqualTo(new Adjusted(Map.of(), List.of(), List.of(DEVELOPED_WORLD_FUND)));
  }

  @Test
  void anExchangeTradedFundKeepsItsFreshestPrice() {
    given(instrumentReferenceService.findByIsin("IE000QWCYQT0"))
        .willReturn(
            Optional.of(
                instrument("IE000QWCYQT0")
                    .benchmarkCategory("EQUITY_DM")
                    .instrumentType("ETF")
                    .eodhdTicker("WLSC.XETRA")
                    .build()));

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of("IE000QWCYQT0"), POSITION_DATE))
        .isEqualTo(Adjusted.NONE);
  }

  @Test
  void anEtfIsNotAnIndexFundEvenWithoutAnExchangeTicker() {
    given(instrumentReferenceService.findByIsin("IE00ETF"))
        .willReturn(
            Optional.of(
                instrument("IE00ETF")
                    .benchmarkCategory("EQUITY_EM")
                    .instrumentType("ETF")
                    .build()));

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of("IE00ETF"), POSITION_DATE))
        .isEqualTo(Adjusted.NONE);
  }

  @Test
  void aFundOutsideTheDevelopedAndEmergingEquityCategoriesKeepsItsFreshestPrice() {
    givenIndexFund("IE0005032192", "BOND_EURO");

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of("IE0005032192"), POSITION_DATE))
        .isEqualTo(Adjusted.NONE);
  }

  @Test
  void anInstrumentWithoutReferenceDataKeepsItsFreshestPrice() {
    given(instrumentReferenceService.findByIsin("UNKNOWN")).willReturn(Optional.empty());

    assertThat(indexAdjustedFundPrices.onPositionDate(List.of("UNKNOWN"), POSITION_DATE))
        .isEqualTo(Adjusted.NONE);
  }

  private void givenIndexFund(String isin, String benchmarkCategory) {
    given(instrumentReferenceService.findByIsin(isin))
        .willReturn(
            Optional.of(
                instrument(isin)
                    .benchmarkCategory(benchmarkCategory)
                    .instrumentType("FUND")
                    .eodhdTicker(isin + ".EUFUND")
                    .build()));
  }

  private void givenNav(String isin, LocalDate date, String value) {
    given(positionPriceResolver.resolve(isin, POSITION_DATE))
        .willReturn(Optional.of(nav(date, value)));
  }

  private void givenLevel(String key, LocalDate date, String level) {
    given(fundValueProvider.getValueForDate(key, date))
        .willReturn(Optional.of(aFundValue(key, date, new BigDecimal(level))));
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
