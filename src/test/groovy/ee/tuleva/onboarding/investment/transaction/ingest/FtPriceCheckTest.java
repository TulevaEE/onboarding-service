package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PRICE_MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtPriceCheckTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate MONDAY_TRADE_DATE = LocalDate.of(2026, 6, 8);
  private static final LocalDate THURSDAY_BEFORE_EASTER = LocalDate.of(2026, 4, 2);
  private static final BigDecimal PRICE = new BigDecimal("10.09");

  @Mock private PositionPriceResolver positionPriceResolver;

  private final Map<String, String> details = new LinkedHashMap<>();

  private FtPriceCheck priceCheck(String now, LocalDate alertsFrom) {
    return new FtPriceCheck(
        positionPriceResolver,
        new PriceValidator(),
        new Target2Calendar(),
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
        alertsFrom);
  }

  @Test
  void closingPriceMissingUntilTheEndOfTheNextBusinessDay_isPendingNav() {
    FtVerificationStatus status =
        priceCheck("2026-06-09T15:00:00Z", MONDAY_TRADE_DATE)
            .check(ISIN, MONDAY_TRADE_DATE, PRICE, details);

    assertThat(status).isEqualTo(PENDING_NAV);
    assertThat(details).isEmpty();
  }

  @Test
  void closingPriceStillMissingAfterTheNextBusinessDay_isPriceMissing() {
    FtVerificationStatus status =
        priceCheck("2026-06-10T06:00:00Z", MONDAY_TRADE_DATE)
            .check(ISIN, MONDAY_TRADE_DATE, PRICE, details);

    assertThat(status).isEqualTo(PRICE_MISSING);
    assertThat(details).containsExactly(entry("closingPriceDueBy", "2026-06-09"));
  }

  @Test
  void onlyAnEarlierDaysClosingPriceAfterTheNextBusinessDay_isPriceMissing() {
    givenClosingPrice(PRICE, MONDAY_TRADE_DATE.minusDays(3));

    FtVerificationStatus status =
        priceCheck("2026-06-10T06:00:00Z", MONDAY_TRADE_DATE)
            .check(ISIN, MONDAY_TRADE_DATE, PRICE, details);

    assertThat(status).isEqualTo(PRICE_MISSING);
  }

  @Test
  void closingPriceMissingOverTheEasterHolidays_waitsUntilTheEndOfTheNextTarget2BusinessDay() {
    FtVerificationStatus status =
        priceCheck("2026-04-07T15:00:00Z", THURSDAY_BEFORE_EASTER)
            .check(ISIN, THURSDAY_BEFORE_EASTER, PRICE, details);

    assertThat(status).isEqualTo(PENDING_NAV);
  }

  @Test
  void closingPriceMissingForATradeBeforeAlertsFrom_staysPendingNav() {
    FtVerificationStatus status =
        priceCheck("2026-06-10T06:00:00Z", MONDAY_TRADE_DATE.plusDays(1))
            .check(ISIN, MONDAY_TRADE_DATE, PRICE, details);

    assertThat(status).isEqualTo(PENDING_NAV);
    assertThat(details).isEmpty();
  }

  @Test
  void closingPriceArrivingAfterTheNextBusinessDay_clearsTheCheckToOk() {
    givenClosingPrice(PRICE, MONDAY_TRADE_DATE);

    FtVerificationStatus status =
        priceCheck("2026-06-15T06:00:00Z", MONDAY_TRADE_DATE)
            .check(ISIN, MONDAY_TRADE_DATE, PRICE, details);

    assertThat(status).isEqualTo(OK);
    assertThat(details).containsExactly(entry("referencePrice", "10.09"));
  }

  private void givenClosingPrice(BigDecimal price, LocalDate priceDate) {
    given(positionPriceResolver.resolve(ISIN, MONDAY_TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(price)
                    .priceDate(priceDate)
                    .validationStatus(ValidationStatus.OK)
                    .build()));
  }
}
