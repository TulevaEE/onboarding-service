package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NavLookupTest {

  private static final String ISIN = "IE000F60HVH9";
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 5, 11);

  @Autowired private NavLookup navLookup;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void clean() {
    jdbcClient.sql("DELETE FROM nav_report WHERE account_id = :isin").param("isin", ISIN).update();
  }

  @AfterEach
  void cleanup() {
    jdbcClient.sql("DELETE FROM nav_report WHERE account_id = :isin").param("isin", ISIN).update();
  }

  @Test
  void findMarketPrice_returnsMatchingRow() {
    insertNavRow(ISIN, NAV_DATE, new BigDecimal("4.7255"));

    Optional<BigDecimal> price = navLookup.findMarketPrice(ISIN, NAV_DATE);

    assertThat(price).isPresent();
    assertThat(price.get()).isEqualByComparingTo("4.7255");
  }

  @Test
  void findMarketPrice_returnsEmptyWhenNoRow() {
    Optional<BigDecimal> price = navLookup.findMarketPrice(ISIN, NAV_DATE);
    assertThat(price).isEmpty();
  }

  @Test
  void findMarketPrice_ignoresDifferentDate() {
    insertNavRow(ISIN, NAV_DATE.minusDays(1), new BigDecimal("4.6900"));
    insertNavRow(ISIN, NAV_DATE.plusDays(1), new BigDecimal("4.7500"));

    Optional<BigDecimal> price = navLookup.findMarketPrice(ISIN, NAV_DATE);
    assertThat(price).isEmpty();
  }

  @Test
  void findMarketPrice_ignoresNonSecurityRows() {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, market_price,
               currency, calculation_id, published_at)
            VALUES (:navDate, 'TKF100', 'CASH', :name, :isin, :marketPrice,
               'EUR', :calcId, now())
            """)
        .param("navDate", NAV_DATE)
        .param("name", "CASH-" + ISIN)
        .param("isin", ISIN)
        .param("marketPrice", new BigDecimal("1.0000"))
        .param("calcId", UUID.randomUUID())
        .update();

    Optional<BigDecimal> price = navLookup.findMarketPrice(ISIN, NAV_DATE);
    assertThat(price).isEmpty();
  }

  @Test
  void findMarketPrice_dedupesAcrossFunds() {
    // Same ISIN held by two funds → both rows are SECURITY with same nav_date.
    // Multiple matches must collapse to one consistent price; pick any (they must
    // be equal in practice but we only require non-empty Optional<BigDecimal>).
    insertNavRowForFund(ISIN, NAV_DATE, "TKF100", new BigDecimal("4.7255"));
    insertNavRowForFund(ISIN, NAV_DATE, "TUK75", new BigDecimal("4.7255"));

    Optional<BigDecimal> price = navLookup.findMarketPrice(ISIN, NAV_DATE);
    assertThat(price).isPresent();
    assertThat(price.get()).isEqualByComparingTo("4.7255");
  }

  private void insertNavRow(String isin, LocalDate navDate, BigDecimal marketPrice) {
    insertNavRowForFund(isin, navDate, "TKF100", marketPrice);
  }

  private void insertNavRowForFund(
      String isin, LocalDate navDate, String fundCode, BigDecimal marketPrice) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, market_price,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, 'SECURITY', :name, :isin, :marketPrice,
               'EUR', :calcId, now())
            """)
        .param("navDate", navDate)
        .param("fundCode", fundCode)
        .param("name", fundCode + "-" + isin)
        .param("isin", isin)
        .param("marketPrice", marketPrice)
        .param("calcId", UUID.randomUUID())
        .update();
  }
}
