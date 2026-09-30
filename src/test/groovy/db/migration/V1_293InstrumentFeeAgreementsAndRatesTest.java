package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class V1_293InstrumentFeeAgreementsAndRatesTest {

  private static final String URL =
      "jdbc:h2:mem:instrumentfeeagreementmigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_AGREEMENTS = "1.287?";
  private static final String THE_AGREEMENTS = "1.293";

  private static final String REBATE_ISIN = "ZZ0000000001";
  private static final String INVOICED_FEE_ISIN = "ZZ0000000002";
  private static final String NO_AGREEMENT_ISIN = "ZZ0000000003";
  private static final LocalDate VALID_FROM = LocalDate.of(2026, 3, 1);
  private static final LocalDate VALID_TO = LocalDate.of(2026, 3, 31);

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheAgreements() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_AGREEMENTS);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void aStoredRebateBecomesAFixedRebateAgreementWithThatRate() {
    givenARateBeforeTheAgreements(REBATE_ISIN, "0.00200000", "0.00050000", "0.00000000", VALID_TO);

    migrateTo(THE_AGREEMENTS);

    assertThat(agreementOf(REBATE_ISIN))
        .isEqualTo(new Agreement("0.00200000", "FIXED", "0.00000000", VALID_FROM, VALID_TO));
    assertThat(termsOf(REBATE_ISIN)).contains("rate").contains("0.00050000");
  }

  @Test
  void anInvoicedFeeWithNoRebateKeepsItsFeeOnAnAgreementWithNoRebate() {
    givenARateBeforeTheAgreements(
        INVOICED_FEE_ISIN, "0.00070000", "0.00000000", "0.00040000", null);

    migrateTo(THE_AGREEMENTS);

    assertThat(agreementOf(INVOICED_FEE_ISIN))
        .isEqualTo(new Agreement("0.00070000", "NONE", "0.00040000", VALID_FROM, null));
  }

  @Test
  void aRateWithNeitherARebateNorAFeeBecomesAnAgreementWithNoRebate() {
    givenARateBeforeTheAgreements(
        NO_AGREEMENT_ISIN, "0.00120000", "0.00000000", "0.00000000", null);

    migrateTo(THE_AGREEMENTS);

    assertThat(agreementOf(NO_AGREEMENT_ISIN))
        .isEqualTo(new Agreement("0.00120000", "NONE", "0.00000000", VALID_FROM, null));
    assertThat(termsOf(NO_AGREEMENT_ISIN)).doesNotContain("rate");
  }

  @Test
  void aRateThatDoesNotAddUpIsRefused() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);
    long agreementId = givenAnAgreement(REBATE_ISIN, "FIXED", "0.00000000");

    assertThatThrownBy(
            () -> givenARate(agreementId, "0.00200000", "0.00050000", "0.00000000", "0.00200000"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRateThatAddsUpIsStored() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);
    long agreementId = givenAnAgreement(REBATE_ISIN, "FIXED", "0.00000000");

    givenARate(agreementId, "0.00200000", "0.00050000", "0.00010000", "0.00160000");

    assertThat(
            jdbcClient
                .sql("SELECT COUNT(*) FROM investment_instrument_fee_rate")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void aFixedNetAgreementCannotCarryAnInvoicedFeeBesideItsNet() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);

    assertThatThrownBy(() -> givenAnAgreement(REBATE_ISIN, "FIXED_NET", "0.00010000"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAgreementOfARebateKindTheCalculationDoesNotKnowIsRefused() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);

    assertThatThrownBy(() -> givenAnAgreement(REBATE_ISIN, "CASHBACK", "0.00000000"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aRateOnABasisOtherThanTheAgreementOrItsFallbackIsRefused() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);
    long agreementId = givenAnAgreement(REBATE_ISIN, "FIXED", "0.00000000");

    assertThatThrownBy(() -> givenARateOnTheBasis(agreementId, "ESTIMATE", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aFallbackRateMustSayWhyAndARateFromTheAgreementMustNot() {
    migrateTo(THE_AGREEMENTS);
    inTheInstrumentReference(REBATE_ISIN);
    long agreementId = givenAnAgreement(REBATE_ISIN, "FIXED", "0.00000000");

    assertThatThrownBy(() -> givenARateOnTheBasis(agreementId, "PUBLISHED_FALLBACK", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> givenARateOnTheBasis(agreementId, "AGREEMENT", "a reason"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAgreementForAnInstrumentTheReferenceDoesNotKnowIsRefused() {
    migrateTo(THE_AGREEMENTS);

    assertThatThrownBy(() -> givenAnAgreement("XX0000000001", "NONE", "0.00000000"))
        .isInstanceOf(DataIntegrityViolationException.class);
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

  private void givenARateBeforeTheAgreements(
      String isin,
      String publishedOcf,
      String rebateRate,
      String invoicedFeeRate,
      @Nullable LocalDate validTo) {
    var published = new BigDecimal(publishedOcf);
    var rebate = new BigDecimal(rebateRate);
    var invoiced = new BigDecimal(invoicedFeeRate);
    inTheInstrumentReference(isin);
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, instrument_name, published_ocf, rebate_rate, invoiced_fee_rate, net_ocf,
               valid_from, valid_to)
            VALUES (:isin, :isin, :published, :rebate, :invoiced, :net, :validFrom, :validTo)
            """)
        .param("isin", isin)
        .param("published", published)
        .param("rebate", rebate)
        .param("invoiced", invoiced)
        .param("net", published.subtract(rebate).add(invoiced))
        .param("validFrom", VALID_FROM)
        .param("validTo", validTo)
        .update();
  }

  private void inTheInstrumentReference(String isin) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference (isin, display_name, instrument_type, asset_class)
            VALUES (:isin, :isin, 'FUND', 'equity')
            """)
        .param("isin", isin)
        .update();
  }

  private long givenAnAgreement(String isin, String rebateKind, String invoicedFeeRate) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee
              (isin, published_ocf, rebate_kind, rebate_terms, invoiced_fee_rate, valid_from)
            VALUES (:isin, 0.00200000, :rebateKind, '{}', :invoicedFeeRate, :validFrom)
            """)
        .param("isin", isin)
        .param("rebateKind", rebateKind)
        .param("invoicedFeeRate", new BigDecimal(invoicedFeeRate))
        .param("validFrom", VALID_FROM)
        .update();
    return jdbcClient
        .sql("SELECT id FROM investment_instrument_fee WHERE isin = :isin")
        .param("isin", isin)
        .query(Long.class)
        .single();
  }

  private void givenARate(
      long agreementId,
      String publishedOcf,
      String rebateRate,
      String invoicedFeeRate,
      String net) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, rebate_kind, instrument_fee_id)
            VALUES (:isin, :periodStart, :periodEnd, :published, :rebate, :invoiced, :net,
                    'AGREEMENT', 'FIXED', :agreementId)
            """)
        .param("isin", REBATE_ISIN)
        .param("periodStart", VALID_FROM)
        .param("periodEnd", VALID_TO)
        .param("published", new BigDecimal(publishedOcf))
        .param("rebate", new BigDecimal(rebateRate))
        .param("invoiced", new BigDecimal(invoicedFeeRate))
        .param("net", new BigDecimal(net))
        .param("agreementId", agreementId)
        .update();
  }

  private void givenARateOnTheBasis(
      long agreementId, String rateBasis, @Nullable String fallbackReason) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_instrument_fee_rate
              (isin, period_start, period_end, published_ocf, rebate_rate, invoiced_fee_rate,
               net_ocf, rate_basis, fallback_reason, rebate_kind, instrument_fee_id)
            VALUES (:isin, :periodStart, :periodEnd, 0.00200000, 0, 0, 0.00200000, :rateBasis,
                    :fallbackReason, 'FIXED', :agreementId)
            """)
        .param("isin", REBATE_ISIN)
        .param("periodStart", VALID_FROM)
        .param("periodEnd", VALID_TO)
        .param("rateBasis", rateBasis)
        .param("fallbackReason", fallbackReason)
        .param("agreementId", agreementId)
        .update();
  }

  private Agreement agreementOf(String isin) {
    return jdbcClient
        .sql(
            """
            SELECT published_ocf, rebate_kind, invoiced_fee_rate, valid_from, valid_to
            FROM investment_instrument_fee WHERE isin = :isin
            """)
        .param("isin", isin)
        .query(
            (rs, rowNum) ->
                new Agreement(
                    rs.getBigDecimal("published_ocf").toPlainString(),
                    rs.getString("rebate_kind"),
                    rs.getBigDecimal("invoiced_fee_rate").toPlainString(),
                    rs.getDate("valid_from").toLocalDate(),
                    rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate()))
        .single();
  }

  private String termsOf(String isin) {
    return jdbcClient
        .sql(
            "SELECT CAST(rebate_terms AS varchar(200)) FROM investment_instrument_fee WHERE isin = :isin")
        .param("isin", isin)
        .query(String.class)
        .single();
  }

  private record Agreement(
      String publishedOcf,
      String rebateKind,
      String invoicedFeeRate,
      LocalDate validFrom,
      @Nullable LocalDate validTo) {}
}
