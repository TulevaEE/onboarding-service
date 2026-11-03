package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.transaction.AfterTransaction;

@DataJpaTest
@ComponentScan(basePackageClasses = InstrumentReferenceService.class)
@Import(ClockConfig.class)
class BenchmarkProxyPeriodTest {

  private static final LocalDate LAST_DAY_ON_EUNA = LocalDate.parse("2026-08-24");
  private static final LocalDate FIRST_DAY_ON_GAGH = LocalDate.parse("2026-08-25");
  private static final LocalDate LAST_DAY_BEFORE_U09_SWAP = LocalDate.parse("2026-10-21");
  private static final LocalDate U09_SWAP_DAY = LocalDate.parse("2026-10-22");

  private static final String EUN4 = "IE00B3DKXQ41";
  private static final String EUNA = "IE00BDBRDM35";
  private static final String GAGH = "LU1708330318";
  private static final String SYBA = "IE00B41RYL63";
  private static final String GAGG = "LU1437024729";

  private static final String EURO_AGGREGATE_FUND = "LU0826455353";
  private static final String EURO_GOVERNMENT_FUND = "IE0031080751";
  private static final String EURO_CREDIT_FUND = "IE0005032192";
  private static final String GLOBAL_GOVERNMENT_FUND = "LU0839970364";

  @Autowired private InstrumentReferenceService service;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void swapTheBondProxiesAsU09DidInProduction() {
    insertProxyEtf(SYBA, "SYBA.XETRA");
    insertProxyEtf(GAGG, "GAGG.PA.EODHD");
    repoint("BOND_EURO", SYBA);
    repoint("BOND_GLOBAL", GAGG);
    jdbcClient
        .sql("UPDATE instrument_reference SET benchmark_category = 'BOND_GLOBAL' WHERE isin = ?")
        .param(EURO_CREDIT_FUND)
        .update();
    service.refresh();
  }

  @AfterTransaction
  void reloadTheRolledBackReferenceData() {
    service.refresh();
  }

  @Test
  void aDateBeforeTheSwapIsMeasuredAgainstTheProxyInEffectOnThatDate() {
    assertThat(proxyIsin(EURO_AGGREGATE_FUND, LAST_DAY_BEFORE_U09_SWAP)).isEqualTo(EUN4);
    assertThat(proxyIsin(EURO_GOVERNMENT_FUND, LAST_DAY_BEFORE_U09_SWAP)).isEqualTo(EUN4);
  }

  @Test
  void theSwapDayAndLaterAreMeasuredAgainstTodaysMapping() {
    assertThat(proxyIsin(EURO_AGGREGATE_FUND, U09_SWAP_DAY)).isEqualTo(SYBA);
    assertThat(proxyIsin(GLOBAL_GOVERNMENT_FUND, U09_SWAP_DAY)).isEqualTo(GAGG);
  }

  @Test
  void aHoldingWithTwoPastProxiesUsesThePeriodThatCoversTheDate() {
    assertThat(proxyIsin(GLOBAL_GOVERNMENT_FUND, LocalDate.parse("2026-04-01"))).isEqualTo(EUNA);
    assertThat(proxyIsin(GLOBAL_GOVERNMENT_FUND, LAST_DAY_ON_EUNA)).isEqualTo(EUNA);
    assertThat(proxyIsin(GLOBAL_GOVERNMENT_FUND, FIRST_DAY_ON_GAGH)).isEqualTo(GAGH);
    assertThat(proxyIsin(GLOBAL_GOVERNMENT_FUND, LAST_DAY_BEFORE_U09_SWAP)).isEqualTo(GAGH);
  }

  @Test
  void aHoldingThatChangedCategoryKeepsItsOldCategorysProxyForPastDates() {
    assertThat(proxyIsin(EURO_CREDIT_FUND, LAST_DAY_BEFORE_U09_SWAP)).isEqualTo(EUN4);
    assertThat(proxyIsin(EURO_CREDIT_FUND, U09_SWAP_DAY)).isEqualTo(GAGG);
  }

  @Test
  void aRetiredPastProxyStillResolvesToItsPriceSeries() {
    jdbcClient
        .sql("UPDATE instrument_reference SET active = false WHERE isin IN (?, ?)")
        .params(EUN4, GAGH)
        .update();
    service.refresh();

    var proxy = service.resolveBenchmarkProxy(EURO_AGGREGATE_FUND, LAST_DAY_BEFORE_U09_SWAP);

    assertThat(proxy.orElseThrow().storageKey()).isEqualTo("IE00B3DKXQ41.XETR");
  }

  @Test
  void aHoldingWithNoPastPeriodUsesTodaysMappingOnAnyDate() {
    var proxy = service.resolveBenchmarkProxy("IE00BFG1TM61", LocalDate.parse("2026-04-01"));

    assertThat(proxy.orElseThrow().storageKey()).isEqualTo("MSCI_WORLD");
  }

  @Test
  void anInstrumentThatIsNotAHoldingHasNoBenchmark() {
    assertThat(service.resolveBenchmarkProxy(SYBA, U09_SWAP_DAY)).isEmpty();
    assertThat(service.resolveBenchmarkProxy("IE00NOTATRACKER", U09_SWAP_DAY)).isEmpty();
  }

  private String proxyIsin(String holdingIsin, LocalDate date) {
    return service
        .resolveBenchmarkProxy(holdingIsin, date)
        .orElseThrow()
        .proxyInstrument()
        .getIsin();
  }

  private void insertProxyEtf(String isin, String eodhdTicker) {
    jdbcClient
        .sql(
            "INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class,"
                + " eodhd_ticker) VALUES (?, ?, 'ETF', 'bond', ?)")
        .params(isin, isin, eodhdTicker)
        .update();
  }

  private void repoint(String category, String proxyIsin) {
    jdbcClient
        .sql(
            "UPDATE benchmark_category_proxy SET etf_proxy_isin = ?, index_proxy_isin = ?"
                + " WHERE benchmark_category = ?")
        .params(proxyIsin, proxyIsin, category)
        .update();
  }
}
