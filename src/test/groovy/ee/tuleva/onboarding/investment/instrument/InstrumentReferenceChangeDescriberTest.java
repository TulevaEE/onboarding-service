package ee.tuleva.onboarding.investment.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.instrument.InstrumentReferenceChange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InstrumentReferenceChangeDescriberTest {

  private static final Instant CHANGED_AT = Instant.parse("2026-08-24T07:05:00Z");

  private final InstrumentReferenceChangeDescriber describer =
      new InstrumentReferenceChangeDescriber(new JsonMapper());

  @Test
  void describesAnUpdateAsTheFieldsThatMoved() {
    var change =
        new InstrumentReferenceChange(
            1L,
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

            UPDATE IE00B4L5Y983 by ops-console at 2026-08-24T07:05:00Z
              benchmark_category: EQUITY_DM -> EQUITY_EM

            """);
  }

  @Test
  void describesAnInsertAsItsNonNullValues() {
    var change =
        new InstrumentReferenceChange(
            1L,
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

            INSERT IE00NEW00000 by ops-console at 2026-08-24T07:05:00Z
              isin: IE00NEW00000
              display_name: New ETF

            """);
  }

  @Test
  void describesADeleteAsTheValuesThatWereRemoved() {
    var change =
        new InstrumentReferenceChange(
            1L,
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

            DELETE IE00OLD00000 by ops-console at 2026-08-24T07:05:00Z
              isin: IE00OLD00000
              display_name: Retired ETF

            """);
  }

  @Test
  void failsInsteadOfDescribingAnUpdateWhoseOldValuesCannotBeParsed() {
    var change =
        new InstrumentReferenceChange(
            1L,
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
        new InstrumentReferenceChange(
            1L,
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
        new InstrumentReferenceChange(
            1L, "IE00B4L5Y983", "UPDATE", "ops-console", CHANGED_AT, null, null);

    assertThatThrownBy(() -> describer.describe(List.of(change)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void describesEveryChangeInTheSetInOneBody() {
    var first =
        new InstrumentReferenceChange(
            1L, "IE00AAA00000", "UPDATE", "ops-console", CHANGED_AT, "{\"a\": 1}", "{\"a\": 2}");
    var second =
        new InstrumentReferenceChange(
            2L, "IE00BBB00000", "UPDATE", "migration", CHANGED_AT, "{\"b\": 1}", "{\"b\": 3}");

    assertThat(describer.describe(List.of(first, second)))
        .isEqualTo(
            """
            Instrument reference data changed:

            UPDATE IE00AAA00000 by ops-console at 2026-08-24T07:05:00Z
              a: 1 -> 2

            UPDATE IE00BBB00000 by migration at 2026-08-24T07:05:00Z
              b: 1 -> 3

            """);
  }
}
