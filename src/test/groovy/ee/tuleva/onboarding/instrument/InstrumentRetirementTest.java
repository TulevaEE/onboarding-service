package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.time.ClockConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({
  InstrumentRetirement.class,
  InstrumentReferenceService.class,
  InstrumentSnapshotLoader.class,
  BenchmarkCategoryProxyRepository.class,
  ClockConfig.class
})
@Transactional(propagation = NOT_SUPPORTED)
class InstrumentRetirementTest {

  private static final String RETIRING_ISIN = "IE00RETIRE01";
  private static final String NEIGHBOUR_ISIN = "IE00RETIRE02";
  private static final List<String> ISINS_THIS_TEST_INSERTS =
      List.of(RETIRING_ISIN, NEIGHBOUR_ISIN);

  @Autowired private InstrumentRetirement instrumentRetirement;
  @Autowired private InstrumentReferenceService instrumentReferenceService;
  @Autowired private JdbcClient jdbcClient;

  @AfterEach
  void removeTheInstrumentsThisTestCommitted() {
    jdbcClient
        .sql("DELETE FROM instrument_reference WHERE isin IN (:isins)")
        .param("isins", ISINS_THIS_TEST_INSERTS)
        .update();
    jdbcClient
        .sql("DELETE FROM reference_data_history WHERE record_key IN (:isins)")
        .param("isins", ISINS_THIS_TEST_INSERTS)
        .update();
  }

  @Test
  void retiresEveryActiveInstrumentItIsGivenAndKeepsTheirRows() {
    insertInstrument(RETIRING_ISIN, true);
    insertInstrument(NEIGHBOUR_ISIN, true);

    var outcome = instrumentRetirement.retire(List.of(RETIRING_ISIN, NEIGHBOUR_ISIN));

    assertThat(outcome)
        .isEqualTo(
            new InstrumentRetirementOutcome(
                List.of(RETIRING_ISIN, NEIGHBOUR_ISIN), List.of(), true));
    assertThat(isActive(RETIRING_ISIN)).isFalse();
    assertThat(isActive(NEIGHBOUR_ISIN)).isFalse();
  }

  @Test
  void reportsNothingAndDoesNotReloadTheCacheWhenTheInstrumentWasAlreadyRetired() {
    insertInstrument(RETIRING_ISIN, false);

    var outcome = instrumentRetirement.retire(List.of(RETIRING_ISIN));

    assertThat(outcome).isEqualTo(new InstrumentRetirementOutcome(List.of(), List.of(), false));
    assertThat(isActive(RETIRING_ISIN)).isFalse();
  }

  @Test
  void retiresOnlyTheInstrumentsItWasAskedTo() {
    insertInstrument(RETIRING_ISIN, true);
    insertInstrument(NEIGHBOUR_ISIN, true);

    instrumentRetirement.retire(List.of(RETIRING_ISIN));

    assertThat(isActive(NEIGHBOUR_ISIN)).isTrue();
  }

  @Test
  void reloadsThisInstancesInstrumentCacheOnceSomethingRetired() {
    insertInstrument(RETIRING_ISIN, true);

    instrumentRetirement.retire(List.of(RETIRING_ISIN));

    assertThat(instrumentReferenceService.findByIsin(RETIRING_ISIN))
        .hasValueSatisfying(instrument -> assertThat(instrument.isActive()).isFalse());
  }

  private void insertInstrument(String isin, boolean active) {
    jdbcClient
        .sql(
            """
            INSERT INTO instrument_reference
              (isin, display_name, instrument_type, asset_class, eodhd_listed, active)
            VALUES (:isin, :isin, 'ETF', 'equity', false, :active)
            """)
        .param("isin", isin)
        .param("active", active)
        .update();
  }

  private boolean isActive(String isin) {
    return jdbcClient
        .sql("SELECT active FROM instrument_reference WHERE isin = :isin")
        .param("isin", isin)
        .query(Boolean.class)
        .single();
  }
}
