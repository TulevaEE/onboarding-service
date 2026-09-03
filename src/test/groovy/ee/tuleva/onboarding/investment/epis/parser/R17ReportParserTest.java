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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Teine PF valitseja;0.80;80.000;120.000;160.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;Teine PF valitseja;0.80;20.000;30.000;40.00;0.00
        Tuleva Maailma Võlakirjade Pensionifond;0.70;Väljalase;Oma;0.70;100.000;200.000;210.00;0.00
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
  void sumsFeeFreeAndFeeBearingUnitColumns() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Esialgne nimekiri moodustatud;;;;16.08.2026;;EUR;;
        %s
        TUK00;0.61267;Tagasivõtt;Oma PF valitseja;0.61267;400000.000;300000.000;428869.00;0
        TUK00;0.61267;Tagasivõtt;Teised PF valitsejad;0.61267;300000.000;200000.000;306335.00;0
        TUK00;0.61267;Tagasivõtt;PIK;0.61267;0;1000.000;612.67;0
        TUK00;0.61267;Väljalase;Oma PF valitseja;0.61267;0;150000.000;91900.50;0
        TUK00;0.61267;Väljalase;Teised PF valitsejad;0.61267;100000.000;400000.000;306335.00;0
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result =
        parser.parse(csv, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1));

    assertThat(result.get("TUK00").pikUnits()).isEqualByComparingTo("1000.000");
    assertThat(result.get("TUK00").switchingNetUnits()).isEqualByComparingTo("-550000.000");
  }

  @Test
  void throwsWhenSeisugaDateOutsideCycleWindow() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.03.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;Teine PF valitseja;0.80;-20.000;-30.000;-40.00;0.00
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
        Mingi Muu Fond;0.80;Väljalase;Oma;0.80;40.000;60.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;40.500;60.000;80.40;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;60000000.000;60000000.000;80.00;0.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;999.00
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;;;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenUnitsTimesPriceDisagreesWithReportedAmount() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;800.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void catchesAMissingUnitComponentTooSmallForAPercentageTolerance() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;1.6167;Väljalase;Teine PF valitseja;1.6167;0;12643992.000;20501430.35;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void catchesADroppedUnitComponentWhenThePriceIsPrintedWithTwoDecimals() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Teine PF valitseja;0.80;40000.000;60000.000;80400.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenThePriceColumnIsPresentButTheRowHasNoPrice() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;;40.000;60.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ignoresAZeroAmountAsNotApplicable() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;0.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void toleratesRoundingBetweenUnitsTimesPriceAndReportedAmount() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.61267;Tagasivõtt;PIK;0.61267;400000.000;300000.000;428869.02;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("700000.000");
  }

  @Test
  void acceptsRowWhenOnlyOneUnitColumnIsPopulated() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;100.000;;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
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
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;PIK;0.80;40.000;60.000;80.00;0.00
        """
                .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void ignoresTheReportedAmountOnRowsForFundsThatAreNotOurs() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Mingi Muu Fond;0.80;Väljalase;Oma;0.80;40.000;60.000;999999.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;40.000;60.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("100.000");
    assertThat(result).doesNotContainKey("MINGIMUUFOND");
  }

  @Test
  void aForeignFundsRowBeyondTheSanityLimitDoesNotRejectTheUpload() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Mingi Muu Fond;0.80;Väljalase;Oma;0.80;60000000.000;60000000.000;96000000.00;0.00
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;40.000;60.000;80.00;0.00
        """
            .formatted(HEADER_ROW);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("100.000");
    assertThat(result).doesNotContainKey("MINGIMUUFOND");
  }

  @Test
  void ourOwnRowBeyondTheSanityLimitStillThrows() {
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;60000000.000;60000000.000;96000000.00;0.00
        """
            .formatted(HEADER_ROW);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsTheRowAmountEvenWhenThePfValitsejaAmountColumnComesFirst() {
    String reorderedHeader =
        "Väärtpaber;NAV;Toiming;PF valitseja/PIK;Hind;Osakud (teenustasuta);Osakud (teenustasuga);Summa (PF valitseja);Summa";
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Väljalase;Oma;0.80;40.000;60.000;999.00;80.00
        """
            .formatted(reorderedHeader);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("100.000");
  }

  @Test
  void classifiesPikByTheOperatorColumnEvenWhenTheAmountColumnComesFirst() {
    // "Summa (PF valitseja)" also contains "pf valitseja", so the bare contains-match lands on
    // whichever of the two columns the export puts first. In the real export the amount column
    // sits to the right, which made the old lookup right by luck of column order rather than by
    // rule. This header moves it left, and without the "pf valitseja/pik" candidate the operator
    // type would read "0.00" — no "pik" in it, so a PIK redemption would be booked as a switching
    // outflow. The row's units are unchanged either way, so the units-vs-amount cross-check cannot
    // catch it.
    String reorderedHeader =
        "Väärtpaber;NAV;Toiming;Summa (PF valitseja);Hind;Osakud (teenustasuta);Osakud (teenustasuga);Summa;PF valitseja/PIK";
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta;;
        Netitud;;;;15.04.2026;;EUR;;
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;0.00;0.80;40.000;60.000;80.00;PIK
        """
            .formatted(reorderedHeader);

    Map<String, R17Result> result = parser.parse(csv, LOCK_DATE, EXEC_DATE);

    assertThat(result.get("TUK75").pikUnits()).isEqualByComparingTo("100.000");
    assertThat(result.get("TUK75").switchingNetUnits()).isEqualByComparingTo("0");
  }

  // Without the operator column the row cannot be classified at all, and an empty operator type
  // reads as "not PIK" — so the units would land in the switching total and the number would look
  // finished while meaning something else.
  @Test
  void refusesARowWithNoOperatorColumnRatherThanCallingItSwitching() {
    String headerWithoutOperator =
        "Väärtpaber;NAV;Toiming;Hind;Osakud (teenustasuta);Osakud (teenustasuga);Summa";
    String csv =
        """
        Staatus;;;;Seisuga;;Valuuta
        Netitud;;;;15.04.2026;;EUR
        %s
        Tuleva Maailma Aktsiate Pensionifond;0.80;Tagasivõtt;0.80;40.000;60.000;80.00
        """
            .formatted(headerWithoutOperator);

    assertThatThrownBy(() -> parser.parse(csv, LOCK_DATE, EXEC_DATE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
