package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriceSource;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.ExecutedPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExitMarkResolverTest {

  private static final String CCF = "IE0009FT4LX4";
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-08-26");
  private static final LocalDate DEALING_NAV_DATE = LocalDate.parse("2026-08-25");
  private static final LocalDate ACCEPTED_ON = LocalDate.parse("2026-08-24");

  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private InstrumentReferenceService instrumentReferenceService;

  @InjectMocks private ExitMarkResolver resolver;

  // SEB states the executed price but never the day the dealing NAV was struck. Its Trade date is
  // when the order was accepted - the CCF redemption was accepted 24.08 and dealt at the NAV
  // published for 25.08 - so pricing the dealing cost off the trade date compares the execution
  // against a NAV two days older and reports market movement, with the wrong sign, as a levy.
  @Test
  void aMutualFundExitIsPricedAgainstTheNavPublishedBeforeTheNavDateNotItsTradeDate() {
    givenInstrument(CCF, "FUND");
    givenPublishedPrice(CCF, DEALING_NAV_DATE, "17.318", DEALING_NAV_DATE);

    var exitMarks = resolver.resolve(NAV_DATE, executedSell(CCF, "17.314", ACCEPTED_ON));

    assertThat(exitMarks)
        .containsExactly(
            Map.entry(
                CCF,
                new ExitMark(
                    new BigDecimal("17.314"),
                    new ExitMark.PublishedPrice(new BigDecimal("17.318"), DEALING_NAV_DATE))));
  }

  // An exchange-traded fund executes on the exchange the day it trades, so its own trade date is
  // the only mark that measures slippage rather than a day of market movement.
  @Test
  void anExchangeTradedFundExitIsPricedAgainstItsOwnTradeDate() {
    givenInstrument("IE000QWCYQT0", "ETF");
    givenPublishedPrice("IE000QWCYQT0", ACCEPTED_ON, "4.973", ACCEPTED_ON);

    var exitMarks = resolver.resolve(NAV_DATE, executedSell("IE000QWCYQT0", "4.98", ACCEPTED_ON));

    assertThat(exitMarks.get("IE000QWCYQT0").published())
        .isEqualTo(new ExitMark.PublishedPrice(new BigDecimal("4.973"), ACCEPTED_ON));
  }

  // A weekend or holiday sits between the dealing NAV and the nav date often enough that requiring
  // the price to carry the previous calendar day's date would lose the attribution every Monday.
  @Test
  void aMutualFundExitAcceptsTheLatestNavPublishedBeforeTheNavDate() {
    var monday = LocalDate.parse("2026-08-31");
    var friday = LocalDate.parse("2026-08-28");
    givenInstrument(CCF, "FUND");
    givenPublishedPrice(CCF, monday.minusDays(1), "17.436", friday);

    var exitMarks = resolver.resolve(monday, executedSell(CCF, "17.43", ACCEPTED_ON));

    assertThat(exitMarks.get(CCF).published())
        .isEqualTo(new ExitMark.PublishedPrice(new BigDecimal("17.436"), friday));
  }

  // The mark still has to carry the executed price so the exit leg reconciles; only the dealing
  // cost waits for a published price it can be attributed against.
  @Test
  void anExitWithNoPublishedPriceStillCarriesTheExecutedPrice() {
    givenInstrument(CCF, "FUND");
    given(positionPriceResolver.resolve(CCF, DEALING_NAV_DATE)).willReturn(Optional.empty());

    var exitMarks = resolver.resolve(NAV_DATE, executedSell(CCF, "17.314", ACCEPTED_ON));

    assertThat(exitMarks.get(CCF)).isEqualTo(new ExitMark(new BigDecimal("17.314"), null));
  }

  // A price the pipeline itself flagged is not an independent mark, so attributing a dealing cost
  // against it would name a number the price validation already said not to trust.
  @Test
  void aPriceTheValidationRejectedDoesNotBecomeADealingCost() {
    givenInstrument(CCF, "FUND");
    given(positionPriceResolver.resolve(CCF, DEALING_NAV_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("17.318"))
                    .priceSource(PriceSource.MORNINGSTAR)
                    .validationStatus(NO_PRICE_DATA)
                    .priceDate(DEALING_NAV_DATE)
                    .storageKey(CCF + ".MORNINGSTAR")
                    .build()));

    var exitMarks = resolver.resolve(NAV_DATE, executedSell(CCF, "17.314", ACCEPTED_ON));

    assertThat(exitMarks.get(CCF).published()).isNull();
  }

  private void givenInstrument(String isin, String instrumentType) {
    given(instrumentReferenceService.findByIsin(isin))
        .willReturn(Optional.of(instrument(isin).instrumentType(instrumentType).build()));
  }

  private void givenPublishedPrice(
      String isin, LocalDate requestedDate, String price, LocalDate priceDate) {
    given(positionPriceResolver.resolve(isin, requestedDate))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal(price))
                    .priceSource(PriceSource.MORNINGSTAR)
                    .validationStatus(OK)
                    .priceDate(priceDate)
                    .storageKey(isin + ".MORNINGSTAR")
                    .build()));
  }

  private Map<String, ExecutedPrice> executedSell(String isin, String price, LocalDate tradeDate) {
    return Map.of(
        isin, new ExecutedPrice(new BigDecimal(price), new BigDecimal("1000"), tradeDate));
  }
}
