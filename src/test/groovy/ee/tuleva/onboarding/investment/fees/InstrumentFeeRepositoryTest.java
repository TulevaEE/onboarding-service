package ee.tuleva.onboarding.investment.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(InstrumentFeeRepository.class)
class InstrumentFeeRepositoryTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private InstrumentFeeRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_instrument_fee").update();
  }

  @Test
  void findValidRateReturnsMatchingRate() {
    insertFee("IE00BFNM3G45", "iShares USA", "0.0007", "0.0002", "0.0005", "2025-01-01", null);

    var result = repository.findValidRate("IE00BFNM3G45", LocalDate.of(2026, 4, 30));

    assertThat(result).isPresent();
    assertThat(result.get().isin()).isEqualTo("IE00BFNM3G45");
    assertThat(result.get().publishedOcf()).isEqualByComparingTo(new BigDecimal("0.0007"));
    assertThat(result.get().rebateRate()).isEqualByComparingTo(new BigDecimal("0.0002"));
    assertThat(result.get().netOcf()).isEqualByComparingTo(new BigDecimal("0.0005"));
  }

  @Test
  void findValidRateReturnsLatestWhenMultipleVersions() {
    insertFee(
        "IE00BFNM3G45", "iShares USA", "0.0007", "0.0000", "0.0007", "2025-01-01", "2025-12-31");
    insertFee("IE00BFNM3G45", "iShares USA", "0.0005", "0.0001", "0.0004", "2026-01-01", null);

    var result = repository.findValidRate("IE00BFNM3G45", LocalDate.of(2026, 4, 30));

    assertThat(result).isPresent();
    assertThat(result.get().netOcf()).isEqualByComparingTo(new BigDecimal("0.0004"));
  }

  @Test
  void findValidRateReturnsEmptyWhenNoMatch() {
    var result = repository.findValidRate("NONEXISTENT", LocalDate.of(2026, 4, 30));

    assertThat(result).isEmpty();
  }

  @Test
  void findValidRateRespectsValidTo() {
    insertFee(
        "IE00BFNM3G45", "iShares USA", "0.0007", "0.0000", "0.0007", "2025-01-01", "2025-06-30");

    var result = repository.findValidRate("IE00BFNM3G45", LocalDate.of(2026, 1, 15));

    assertThat(result).isEmpty();
  }

  @Test
  void findValidRateReturnsRateWithNonNullValidTo() {
    insertFee(
        "IE00BFNM3G45", "iShares USA", "0.0007", "0.0002", "0.0005", "2025-01-01", "2026-12-31");

    var result = repository.findValidRate("IE00BFNM3G45", LocalDate.of(2026, 4, 30));

    assertThat(result).isPresent();
    assertThat(result.get().validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(result.get().source()).isNull();
  }

  // findAllValidRates uses DISTINCT ON (PostgreSQL-only), tested in PostgreSQL profile

  private void insertFee(
      String isin,
      String name,
      String publishedOcf,
      String rebateRate,
      String netOcf,
      String validFrom,
      String validTo) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, instrument_name, published_ocf, rebate_rate, net_ocf, valid_from, valid_to)
            VALUES (:isin, :name, :publishedOcf, :rebateRate, :netOcf, :validFrom, :validTo)
            """)
        .param("isin", isin)
        .param("name", name)
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("rebateRate", new BigDecimal(rebateRate))
        .param("netOcf", new BigDecimal(netOcf))
        .param("validFrom", LocalDate.parse(validFrom))
        .param("validTo", validTo != null ? LocalDate.parse(validTo) : null)
        .update();
  }
}
