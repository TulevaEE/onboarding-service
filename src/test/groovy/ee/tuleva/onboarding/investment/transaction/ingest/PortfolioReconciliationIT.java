package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasis;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(PortfolioReconciliationIT.TestEventRecorder.class)
class PortfolioReconciliationIT {

  private static final String ISIN_A = "IE00BFG1TM61";
  private static final String ISIN_B = "IE0009FT4LX4";
  private static final LocalDate AS_OF = LocalDate.of(2026, 5, 18);

  @Autowired private PortfolioReconciliationService service;
  @Autowired private PortfolioCostBasisRepository costBasisRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private TestEventRecorder recorder;

  @BeforeEach
  void cleanRecorderAndData() {
    recorder.events.clear();
    deleteNavRows();
    deleteCostBasisRows();
  }

  @AfterEach
  void cleanup() {
    deleteNavRows();
    deleteCostBasisRows();
  }

  @Test
  void quantitiesAgree_noEvent() {
    seedCostBasis(ISIN_A, "10000.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF);

    assertThat(recorder.events).isEmpty();
  }

  @Test
  void ourQuantityDiffers_emitsMismatch() {
    seedCostBasis(ISIN_A, "10005.0000");
    seedNavReport(ISIN_A, new BigDecimal("10000.0000"));

    service.reconcile(TUK75, AS_OF);

    assertThat(recorder.events).hasSize(1);
    PortfolioReconciliationMismatchEvent event = recorder.events.get(0);
    assertThat(event.fund()).isEqualTo(TUK75);
    assertThat(event.asOfDate()).isEqualTo(AS_OF);
    assertThat(event.mismatches()).hasSize(1);
    MismatchEntry entry = event.mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isEqualByComparingTo("10000.0000");
    assertThat(entry.delta()).isEqualByComparingTo("5.0000");
  }

  @Test
  void navReportRowMissing_emitsMismatch() {
    seedCostBasis(ISIN_A, "10005.0000");

    service.reconcile(TUK75, AS_OF);

    assertThat(recorder.events).hasSize(1);
    MismatchEntry entry = recorder.events.get(0).mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_A);
    assertThat(entry.ourQuantity()).isEqualByComparingTo("10005.0000");
    assertThat(entry.theirQuantity()).isNull();
  }

  @Test
  void costBasisRowMissing_emitsMismatch() {
    seedNavReport(ISIN_B, new BigDecimal("250.0000"));

    service.reconcile(TUK75, AS_OF);

    assertThat(recorder.events).hasSize(1);
    MismatchEntry entry = recorder.events.get(0).mismatches().get(0);
    assertThat(entry.isin()).isEqualTo(ISIN_B);
    assertThat(entry.ourQuantity()).isNull();
    assertThat(entry.theirQuantity()).isEqualByComparingTo("250.0000");
  }

  private void seedCostBasis(String isin, String quantity) {
    costBasisRepository.save(
        PortfolioCostBasis.builder()
            .fundIsin(TUK75.getIsin())
            .instrumentIsin(isin)
            .asOfDate(AS_OF)
            .quantity(new BigDecimal(quantity))
            .avgUnitCost(BigDecimal.ZERO)
            .totalCost(BigDecimal.ZERO)
            .source("IT_SEED")
            .build());
  }

  private void seedNavReport(String isin, BigDecimal quantity) {
    jdbcClient
        .sql(
            """
            INSERT INTO nav_report
              (nav_date, fund_code, account_type, account_name, account_id, quantity,
               currency, calculation_id, published_at)
            VALUES (:navDate, :fundCode, 'SECURITY', :name, :isin, :quantity,
               'EUR', :calcId, now())
            """)
        .param("navDate", AS_OF)
        .param("fundCode", TUK75.getCode())
        .param("name", TUK75.getCode() + "-" + isin)
        .param("isin", isin)
        .param("quantity", quantity)
        .param("calcId", UUID.randomUUID())
        .update();
  }

  private void deleteNavRows() {
    jdbcClient
        .sql("DELETE FROM nav_report WHERE account_id IN (:isins)")
        .param("isins", List.of(ISIN_A, ISIN_B))
        .update();
  }

  private void deleteCostBasisRows() {
    jdbcClient
        .sql(
            "DELETE FROM investment_portfolio_cost_basis WHERE instrument_isin IN (:isins)"
                + " AND fund_isin = :fundIsin")
        .param("isins", List.of(ISIN_A, ISIN_B))
        .param("fundIsin", TUK75.getIsin())
        .update();
  }

  public static class TestEventRecorder {
    final List<PortfolioReconciliationMismatchEvent> events = new ArrayList<>();

    @EventListener
    void onMismatch(PortfolioReconciliationMismatchEvent event) {
      events.add(event);
    }
  }
}
