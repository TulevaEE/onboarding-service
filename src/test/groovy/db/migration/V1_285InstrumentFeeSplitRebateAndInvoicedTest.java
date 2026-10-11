package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_285InstrumentFeeSplitRebateAndInvoicedTest {

  private static final String URL =
      "jdbc:h2:mem:instrumentfeesplitmigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_SPLIT_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.284?";
  private static final String THE_SPLIT = "1.285";

  private static final String REBATE_ISIN = "XX0000000001";
  private static final String INVOICED_FEE_ISIN = "XX0000000002";
  private static final String NO_AGREEMENT_ISIN = "XX0000000003";

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheSplit() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_SPLIT_EVEN_IF_NO_MIGRATION_HOLDS_IT);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void beforeTheSplitTheRateCarriesOnlyTheSignedRebateColumn() {
    assertThat(rateColumns()).contains("rebate_rate").doesNotContain("invoiced_fee_rate");
  }

  @Test
  void aRebateStoredNegativeBecomesAPositiveRebateWithNoInvoicedFee() {
    long id = givenASignedRateBeforeTheSplit(REBATE_ISIN, "0.0020", "-0.0005", "0.0015");

    migrateTo(THE_SPLIT);

    assertThat(splitRate(id))
        .isEqualTo(new SplitRate(id, REBATE_ISIN, "0.0020", "0.0005", "0.0000", "0.0015"));
  }

  @Test
  void anInvoicedFeeStoredPositiveMovesToItsOwnColumnAndLeavesNoRebate() {
    long id = givenASignedRateBeforeTheSplit(INVOICED_FEE_ISIN, "0.0007", "0.0004", "0.0011");

    migrateTo(THE_SPLIT);

    assertThat(splitRate(id))
        .isEqualTo(new SplitRate(id, INVOICED_FEE_ISIN, "0.0007", "0.0000", "0.0004", "0.0011"));
  }

  @Test
  void aRateWithoutAnAgreementStaysAtZeroInBothColumns() {
    long id = givenASignedRateBeforeTheSplit(NO_AGREEMENT_ISIN, "0.0012", "0.0000", "0.0012");

    migrateTo(THE_SPLIT);

    assertThat(splitRate(id))
        .isEqualTo(new SplitRate(id, NO_AGREEMENT_ISIN, "0.0012", "0.0000", "0.0000", "0.0012"));
  }

  @Test
  void afterTheSplitEveryNetOcfIsPublishedLessTheRebatePlusTheInvoicedFee() {
    givenEachKindOfSignedRateBeforeTheSplit();

    migrateTo(THE_SPLIT);

    assertThat(splitRates())
        .hasSize(3)
        .allSatisfy(
            rate -> assertThat(rate.recomposedNetOcf()).isEqualByComparingTo(rate.netOcf()));
  }

  @Test
  void theSplitKeepsEveryRateUnderTheIdItHadBefore() {
    var idsBeforeTheSplit = givenEachKindOfSignedRateBeforeTheSplit();

    migrateTo(THE_SPLIT);

    assertThat(splitRates().stream().map(SplitRate::id).toList())
        .containsExactlyElementsOf(idsBeforeTheSplit);
  }

  private void migrateTo(String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(FLYWAY_LOCATIONS)
        .baselineOnMigrate(true)
        .target(version)
        .load()
        .migrate();
  }

  private List<Long> givenEachKindOfSignedRateBeforeTheSplit() {
    return List.of(
        givenASignedRateBeforeTheSplit(REBATE_ISIN, "0.0020", "-0.0005", "0.0015"),
        givenASignedRateBeforeTheSplit(INVOICED_FEE_ISIN, "0.0007", "0.0004", "0.0011"),
        givenASignedRateBeforeTheSplit(NO_AGREEMENT_ISIN, "0.0012", "0.0000", "0.0012"));
  }

  private long givenASignedRateBeforeTheSplit(
      String isin, String publishedOcf, String signedRebateRate, String netOcf) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, instrument_name, published_ocf, rebate_rate, net_ocf, valid_from)
            VALUES (:isin, :isin, :publishedOcf, :rebateRate, :netOcf, :validFrom)
            """)
        .param("isin", isin)
        .param("publishedOcf", new BigDecimal(publishedOcf))
        .param("rebateRate", new BigDecimal(signedRebateRate))
        .param("netOcf", new BigDecimal(netOcf))
        .param("validFrom", LocalDate.of(2025, 1, 1))
        .update();
    return rateIdOf(isin);
  }

  private long rateIdOf(String isin) {
    return jdbcClient
        .sql("SELECT id FROM investment_instrument_fee WHERE isin = :isin")
        .param("isin", isin)
        .query(Long.class)
        .single();
  }

  private List<String> rateColumns() {
    return jdbcClient
        .sql(
            """
            SELECT lower(column_name) FROM information_schema.columns
            WHERE lower(table_name) = 'investment_instrument_fee'
            """)
        .query(String.class)
        .list();
  }

  private SplitRate splitRate(long id) {
    return jdbcClient
        .sql(
            """
            SELECT id, isin, published_ocf, rebate_rate, invoiced_fee_rate, net_ocf
            FROM investment_instrument_fee WHERE id = :id
            """)
        .param("id", id)
        .query(SplitRate::fromRow)
        .single();
  }

  private List<SplitRate> splitRates() {
    return jdbcClient
        .sql(
            """
            SELECT id, isin, published_ocf, rebate_rate, invoiced_fee_rate, net_ocf
            FROM investment_instrument_fee ORDER BY id
            """)
        .query(SplitRate::fromRow)
        .list();
  }

  private record SplitRate(
      long id,
      String isin,
      BigDecimal publishedOcf,
      BigDecimal rebateRate,
      BigDecimal invoicedFeeRate,
      BigDecimal netOcf) {

    private static final int STORED_SCALE = 8;

    SplitRate(
        long id,
        String isin,
        String publishedOcf,
        String rebateRate,
        String invoicedFeeRate,
        String netOcf) {
      this(
          id,
          isin,
          atStoredScale(publishedOcf),
          atStoredScale(rebateRate),
          atStoredScale(invoicedFeeRate),
          atStoredScale(netOcf));
    }

    static SplitRate fromRow(ResultSet rs, int rowNum) throws SQLException {
      return new SplitRate(
          rs.getLong("id"),
          rs.getString("isin"),
          rs.getBigDecimal("published_ocf"),
          rs.getBigDecimal("rebate_rate"),
          rs.getBigDecimal("invoiced_fee_rate"),
          rs.getBigDecimal("net_ocf"));
    }

    BigDecimal recomposedNetOcf() {
      return publishedOcf.subtract(rebateRate).add(invoicedFeeRate);
    }

    private static BigDecimal atStoredScale(String rate) {
      return new BigDecimal(rate).setScale(STORED_SCALE);
    }
  }
}
