package ee.tuleva.onboarding.investment.epis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.epis.R17Result;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R17ReportParserTest {

  private static final LocalDate LOCK_DATE = LocalDate.of(2026, 3, 31);
  private static final LocalDate EXEC_DATE = LocalDate.of(2026, 5, 1);
  private static final String HEADER_ROW =
      "Väärtpaber;NAV;Toiming;PF valitseja/PIK;Hind;Osakud (teenustasuta);Osakud (teenustasuga);Summa;Summa (PF valitseja)";

  private final R17ReportParser parser = new R17ReportParser(new EpisCsvParser());

  @Test
  void aggregatesPikAndSwitchingNetUnitsPerFund() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Teine PF valitseja;0.80;200.000;200.000;160.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;Teine PF valitseja;0.80;50.000;50.000;40.00;0.00
        Tuleva Maailma Võlakirjade Pensionifond;0.70;Väljalase;Oma;0.70;300.000;300.000;210.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result).containsOnlyKeys("TUK75", "TUK00");
    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("150.000");
    assertThat(result.get("TUK00").pikUnits()).isEqualByComparingTo("0");
    assertThat(result.get("TUK00").switchingNetUnits()).isEqualByComparingTo("300.000");
  }

  @Test
  void throwsWhenSeisugaDateOutsideCycleWindow() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.03.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsSeisugaDateOnCycleBoundaries() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;31.03.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void throwsWhenSeisugaDateAfterExecutionDate() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.05.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenSeisugaLinePresentButHasNoDateOnEitherLine() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;teadmata;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void skipsNonSeisugaPreHeaderLinesBeforeSeisugaLine() {
    String csv =
        """
        Fondi aruanne;;;;;;;;
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void throwsWhenSeisugaLabelIsOnTheLastPreHeaderLineWithNoFollowingLine() {
    String csv =
        """
        Seisuga teadmata;;;;;;;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenSeisugaMarkerMissing() {
    String csv =
        """
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsSeisugaDateWhenLabelAndValueAreOnTheSameLine() {
    String csv =
        """
        Seisuga: 15.04.2026;;;;;;;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void usesAbsoluteUnits() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;Teine PF valitseja;0.80;-50.000;-50.000;-40.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("-50.000");
  }

  @Test
  void fallsBackToOsakuidColumnWhenTeenustasugaColumnMissing() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        Väärtpaber;Toiming;PF valitseja/PIK;Osakuid
        Tuleva Maailma Aktsiate Pensionifond;Väljalase;Oma;200.000
        """;

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("200.000");
  }

  @Test
  void skipsRowsWithZeroUnitsOrUnknownFund() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;0;0;0.00;0.00
        Mingi Muu Fond;0.80;Väljalase;Oma;0.80;100.000;100.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void doesNotMisinterpretPeriodDecimalUnitsAsThousandsGrouping() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;100.500;100.500;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("100.500");
  }

  @Test
  void throwsWhenUnitsExceedHundredMillion() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;150000000.000;150000000.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pfValitsejaColumnTakesPrecedenceOverSummaPfValitsejaColumnDueToHeaderOrder() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;999.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("0");
  }

  @Test
  void throwsWhenUnitsBlankOnDataRow() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void skipsRowWithEmptyToimingWithoutRequiringUnits() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;;PIK;0.80;;;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result).isEmpty();
  }

  @Test
  void parsesWhenFileStartsWithUtf8Bom() {
    String csv =
        "﻿"
            + """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;100.000;80.00;0.00
        """
                .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }
}
