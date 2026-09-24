package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(InstrumentRetirement.class)
class InstrumentRetirementTest {

  private static final String RETIRING_ISIN = "IE00RETIRE01";
  private static final String NEIGHBOUR_ISIN = "IE00RETIRE02";

  @Autowired private InstrumentRetirement instrumentRetirement;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void retiresAnActiveInstrumentAndKeepsItsRow() {
    insertInstrument(RETIRING_ISIN, true);

    assertThat(instrumentRetirement.retire(RETIRING_ISIN)).isTrue();

    assertThat(isActive(RETIRING_ISIN)).isFalse();
  }

  @Test
  void saysSoWhenTheInstrumentWasAlreadyRetired() {
    insertInstrument(RETIRING_ISIN, false);

    assertThat(instrumentRetirement.retire(RETIRING_ISIN)).isFalse();

    assertThat(isActive(RETIRING_ISIN)).isFalse();
  }

  @Test
  void retiresOnlyTheInstrumentItWasAskedTo() {
    insertInstrument(RETIRING_ISIN, true);
    insertInstrument(NEIGHBOUR_ISIN, true);

    instrumentRetirement.retire(RETIRING_ISIN);

    assertThat(isActive(NEIGHBOUR_ISIN)).isTrue();
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
