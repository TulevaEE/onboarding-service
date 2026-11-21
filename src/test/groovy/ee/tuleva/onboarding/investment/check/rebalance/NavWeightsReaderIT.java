package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(NavWeightsReader.class)
class NavWeightsReaderIT {

  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);
  private static final Instant MORNING = Instant.parse("2026-10-02T09:30:00Z");
  private static final Instant AFTERNOON = Instant.parse("2026-10-02T13:30:00Z");
  private static final RecursiveComparisonConfiguration BIG_DECIMALS_BY_VALUE =
      RecursiveComparisonConfiguration.builder()
          .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .build();

  @Autowired private NavWeightsReader reader;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void eachNavDateIsReadFromItsLatestPublishedCalculationOnly() {
    var date = LocalDate.of(2026, 10, 1);
    var publishedLast = UUID.randomUUID();
    insert(TUV100, date, publishedLast, AFTERNOON, "UNITS", null, "1000");
    insert(TUV100, date, publishedLast, AFTERNOON, "SECURITY", "WORLD", "700");
    insert(TUV100, date, publishedLast, AFTERNOON, "SECURITY", "WORLD", "100");
    insert(TUV100, date, publishedLast, AFTERNOON, "SECURITY", "EMERGING", "150");
    var insertedLastButPublishedFirst = UUID.randomUUID();
    insert(TUV100, date, insertedLastButPublishedFirst, MORNING, "UNITS", null, "900");
    insert(TUV100, date, insertedLastButPublishedFirst, MORNING, "SECURITY", "WORLD", "800");
    var unpublished = UUID.randomUUID();
    insert(TUV100, date, unpublished, null, "UNITS", null, "2000");
    insert(TUV100, date, unpublished, null, "SECURITY", "WORLD", "1900");

    assertThat(reader.between(TUV100, FROM, TO))
        .usingRecursiveFieldByFieldElementComparator(BIG_DECIMALS_BY_VALUE)
        .containsExactly(
            new NavWeights(
                date,
                new BigDecimal("1000"),
                Map.of("WORLD", new BigDecimal("800"), "EMERGING", new BigDecimal("150"))));
  }

  @Test
  void readsOnlyTheFundsNavDatesInsideTheRangeInDateOrder() {
    givenNav(TUV100, LocalDate.of(2026, 9, 30));
    givenNav(TUV100, LocalDate.of(2026, 10, 30));
    givenNav(TUV100, LocalDate.of(2026, 10, 1));
    givenNav(TUV100, LocalDate.of(2026, 11, 2));
    givenNav(TUK75, LocalDate.of(2026, 10, 15));

    assertThat(reader.between(TUV100, FROM, TO))
        .extracting(NavWeights::navDate)
        .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));
  }

  @Test
  void aSecurityRowWithoutAnIsinAndANavDateWithoutAUnitsTotal_areNotMeasured() {
    var measured = LocalDate.of(2026, 10, 1);
    var calculation = UUID.randomUUID();
    insert(TUV100, measured, calculation, MORNING, "UNITS", null, "1000");
    insert(TUV100, measured, calculation, MORNING, "SECURITY", null, "50");
    insert(TUV100, measured, calculation, MORNING, "SECURITY", "WORLD", "900");
    insert(TUV100, measured, calculation, MORNING, "CASH", null, "50");
    var withoutUnits = LocalDate.of(2026, 10, 2);
    insert(TUV100, withoutUnits, UUID.randomUUID(), MORNING, "SECURITY", "WORLD", "900");

    assertThat(reader.between(TUV100, FROM, TO))
        .usingRecursiveFieldByFieldElementComparator(BIG_DECIMALS_BY_VALUE)
        .containsExactly(
            new NavWeights(
                measured, new BigDecimal("1000"), Map.of("WORLD", new BigDecimal("900"))));
  }

  private void givenNav(TulevaFund fund, LocalDate navDate) {
    var calculation = UUID.randomUUID();
    insert(fund, navDate, calculation, MORNING, "UNITS", null, "1000");
    insert(fund, navDate, calculation, MORNING, "SECURITY", "WORLD", "1000");
  }

  private void insert(
      TulevaFund fund,
      LocalDate navDate,
      UUID calculationId,
      @Nullable Instant publishedAt,
      String accountType,
      @Nullable String isin,
      String marketValue) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, market_value,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, :accountType, :accountName, :accountId, :marketValue,
                    'EUR', :calculationId, :publishedAt)
            """)
        .param("navDate", navDate)
        .param("fundCode", fund.getCode())
        .param("accountType", accountType)
        .param("accountName", accountType + ":" + isin + ":" + UUID.randomUUID())
        .param("accountId", isin)
        .param("marketValue", new BigDecimal(marketValue))
        .param("calculationId", calculationId)
        .param("publishedAt", publishedAt == null ? null : Timestamp.from(publishedAt))
        .update();
  }
}
