package ee.tuleva.onboarding.investment.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.instrument.ReferenceDataChange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReferenceDataChangeDescriberTest {

  private static final Instant CHANGED_AT = Instant.parse("2026-08-24T07:05:00Z");
  private static final String INSTRUMENT_REFERENCE = "instrument_reference";
  private static final String BENCHMARK_CATEGORY_PROXY = "benchmark_category_proxy";

  private final ReferenceDataChangeDescriber describer =
      new ReferenceDataChangeDescriber(new JsonMapper());

  @Test
  void describesAnUpdateAsTheFieldsThatMoved() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00B4L5Y983",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            "{\"isin\": \"IE00B4L5Y983\", \"benchmark_category\": \"EQUITY_DM\", \"active\": true}",
            "{\"isin\": \"IE00B4L5Y983\", \"benchmark_category\": \"EQUITY_EM\", \"active\": true}");

    assertThat(describer.describe(List.of(change)))
        .isEqualTo(
            """
            Instrument reference data changed:

            UPDATE instrument_reference IE00B4L5Y983 by ops-console at 2026-08-24T07:05:00Z
              benchmark_category: EQUITY_DM -> EQUITY_EM

            """);
  }

  @Test
  void describesARePointedBenchmarkProxyAsTheCategoryAndTheColumnThatMoved() {
    var change =
        new ReferenceDataChange(
            1L,
            BENCHMARK_CATEGORY_PROXY,
            "BOND_GLOBAL",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            "{\"benchmark_category\": \"BOND_GLOBAL\", \"etf_proxy_isin\": \"IE00BDBRDM35\", \"index_proxy_isin\": \"IE00BDBRDM35\"}",
            "{\"benchmark_category\": \"BOND_GLOBAL\", \"etf_proxy_isin\": \"LU1708330318\", \"index_proxy_isin\": \"LU1708330318\"}");

    assertThat(describer.describe(List.of(change)))
        .isEqualTo(
            """
            Instrument reference data changed:

            UPDATE benchmark_category_proxy BOND_GLOBAL by ops-console at 2026-08-24T07:05:00Z
              etf_proxy_isin: IE00BDBRDM35 -> LU1708330318
              index_proxy_isin: IE00BDBRDM35 -> LU1708330318

            """);
  }

  @Test
  void describesAnInsertAsItsNonNullValues() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00NEW00000",
            "INSERT",
            "ops-console",
            CHANGED_AT,
            null,
            "{\"isin\": \"IE00NEW00000\", \"display_name\": \"New ETF\", \"ric\": null}");

    assertThat(describer.describe(List.of(change)))
        .isEqualTo(
            """
            Instrument reference data changed:

            INSERT instrument_reference IE00NEW00000 by ops-console at 2026-08-24T07:05:00Z
              isin: IE00NEW00000
              display_name: New ETF

            """);
  }

  @Test
  void describesADeleteAsTheValuesThatWereRemoved() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00OLD00000",
            "DELETE",
            "ops-console",
            CHANGED_AT,
            "{\"isin\": \"IE00OLD00000\", \"display_name\": \"Retired ETF\"}",
            null);

    assertThat(describer.describe(List.of(change)))
        .isEqualTo(
            """
            Instrument reference data changed:

            DELETE instrument_reference IE00OLD00000 by ops-console at 2026-08-24T07:05:00Z
              isin: IE00OLD00000
              display_name: Retired ETF

            """);
  }

  @Test
  void failsInsteadOfDescribingAnUpdateWhoseOldValuesCannotBeParsed() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00B4L5Y983",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            "{\"benchmark_category\":",
            "{\"benchmark_category\": \"EQUITY_EM\"}");

    assertThatThrownBy(() -> describer.describe(List.of(change)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsInsteadOfDescribingAnUpdateWhoseNewValuesCannotBeParsed() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00B4L5Y983",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            "{\"benchmark_category\": \"EQUITY_DM\"}",
            "{\"benchmark_category\":");

    assertThatThrownBy(() -> describer.describe(List.of(change)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsOnAHistoryRowThatHasNeitherOldNorNewValues() {
    var change =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00B4L5Y983",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            null,
            null);

    assertThatThrownBy(() -> describer.describe(List.of(change)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void describesEveryChangeInTheSetInOneBody() {
    var instrumentChange =
        new ReferenceDataChange(
            1L,
            INSTRUMENT_REFERENCE,
            "IE00AAA00000",
            "UPDATE",
            "ops-console",
            CHANGED_AT,
            "{\"a\": 1}",
            "{\"a\": 2}");
    var proxyChange =
        new ReferenceDataChange(
            2L,
            BENCHMARK_CATEGORY_PROXY,
            "EQUITY_DM",
            "UPDATE",
            "migration",
            CHANGED_AT,
            "{\"b\": 1}",
            "{\"b\": 3}");

    assertThat(describer.describe(List.of(instrumentChange, proxyChange)))
        .isEqualTo(
            """
            Instrument reference data changed:

            UPDATE instrument_reference IE00AAA00000 by ops-console at 2026-08-24T07:05:00Z
              a: 1 -> 2

            UPDATE benchmark_category_proxy EQUITY_DM by migration at 2026-08-24T07:05:00Z
              b: 1 -> 3

            """);
  }
}
