package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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
class NavReportPositionLookupTest {

  private static final String ISIN_A = "IE00BFG1TM61";
  private static final String ISIN_B = "IE0009FT4LX4";
  private static final LocalDate NAV_DATE = LocalDate.of(2026, 5, 18);

  @Autowired private NavReportPositionLookup lookup;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void clean() {
    deleteSeed();
  }

  @AfterEach
  void cleanup() {
    deleteSeed();
  }

  @Test
  void returnsSecurityQuantitiesForFundAndDate() {
    UUID calcId = UUID.randomUUID();
    insertWithCalcId(NAV_DATE, "TUK75", ISIN_A, new BigDecimal("10000.0000"), true, calcId);
    insertWithCalcId(NAV_DATE, "TUK75", ISIN_B, new BigDecimal("25000.5000"), true, calcId);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).hasSize(2);
    assertThat(result.get(ISIN_A)).isEqualByComparingTo("10000.0000");
    assertThat(result.get(ISIN_B)).isEqualByComparingTo("25000.5000");
  }

  @Test
  void ignoresOtherFunds() {
    insert(NAV_DATE, "TKF100", ISIN_A, new BigDecimal("10000.0000"), true);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void ignoresOtherDates() {
    insert(NAV_DATE.minusDays(1), "TUK75", ISIN_A, new BigDecimal("10000.0000"), true);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void ignoresNonSecurityRows() {
    insertWithType(NAV_DATE, "TUK75", ISIN_A, new BigDecimal("10000.0000"), "CASH", true);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void ignoresUnpublishedCalculations() {
    insert(NAV_DATE, "TUK75", ISIN_A, new BigDecimal("10000.0000"), false);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsLatestPublishedCalculation() {
    // older published calc
    insert(NAV_DATE, "TUK75", ISIN_A, new BigDecimal("9000.0000"), true);
    // newer published calc with different quantity (same fund + date, different calculation_id)
    insert(NAV_DATE, "TUK75", ISIN_A, new BigDecimal("10000.0000"), true);

    Map<String, BigDecimal> result = lookup.findSecurityQuantities(TUK75, NAV_DATE);

    assertThat(result).hasSize(1);
    assertThat(result.get(ISIN_A)).isEqualByComparingTo("10000.0000");
  }

  private void insert(
      LocalDate navDate, String fundCode, String isin, BigDecimal quantity, boolean published) {
    insertWithType(navDate, fundCode, isin, quantity, "SECURITY", published);
  }

  private void insertWithType(
      LocalDate navDate,
      String fundCode,
      String isin,
      BigDecimal quantity,
      String accountType,
      boolean published) {
    insertFull(navDate, fundCode, isin, quantity, accountType, published, UUID.randomUUID());
  }

  private void insertWithCalcId(
      LocalDate navDate,
      String fundCode,
      String isin,
      BigDecimal quantity,
      boolean published,
      UUID calcId) {
    insertFull(navDate, fundCode, isin, quantity, "SECURITY", published, calcId);
  }

  private void insertFull(
      LocalDate navDate,
      String fundCode,
      String isin,
      BigDecimal quantity,
      String accountType,
      boolean published,
      UUID calcId) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, :accountType, :name, :isin, :quantity,
               'EUR', :calcId, """
                + (published ? "now()" : "NULL")
                + ")")
        .param("navDate", navDate)
        .param("fundCode", fundCode)
        .param("accountType", accountType)
        .param("name", fundCode + "-" + isin)
        .param("isin", isin)
        .param("quantity", quantity)
        .param("calcId", calcId)
        .update();
  }

  private void deleteSeed() {
    jdbcClient
        .sql("DELETE FROM nav_report WHERE account_id IN (:isins) AND fund_code IN (:funds)")
        .param("isins", java.util.List.of(ISIN_A, ISIN_B))
        .param("funds", java.util.List.of(TUK75.getCode(), TKF100.getCode()))
        .update();
  }
}
