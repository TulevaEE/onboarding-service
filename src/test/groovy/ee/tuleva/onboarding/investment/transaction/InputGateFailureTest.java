package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R45;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.HoldingMismatch;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPevaRavaReports;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingR16;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoCommonPriceDate;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoR45Loaded;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoRegistryHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoReportSecurities;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoSameDayCash;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.StalePositionReport;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnquantifiedReportHoldings;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.UnreadableSameDayCash;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputGateFailureTest {

  @Test
  void aStalePositionReportNamesTheReportFoundAndTheOneNeeded() {
    assertThat(
            new StalePositionReport(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17))
                .slackLine())
        .isEqualTo(
            "SEB positsiooniraport on 2026-11-13, vaja 2026-11-17: küsi 2026-11-17 raport SEB-lt");
  }

  @Test
  void aFundWithoutAnyPositionReportSaysItIsMissing() {
    assertThat(new MissingPositionReport(LocalDate.of(2026, 11, 17)).slackLine())
        .isEqualTo("SEB positsiooniraport puudub, vaja 2026-11-17: küsi 2026-11-17 raport SEB-lt");
  }

  @Test
  void aMissingR45WithoutAnEarlierOneLeavesTheLatestOut() {
    assertThat(new NoR45Loaded(LocalDate.of(2026, 11, 18)).slackLine())
        .isEqualTo("R45 2026-11-18 puudub: lae R45 EPIS-ist");
  }

  @Test
  void bothMissingPevaRavaReportsAreNamedTogether() {
    assertThat(
            new MissingPevaRavaReports(
                    List.of(R17_PEVA, R21_RAVA),
                    LocalDate.of(2027, 1, 4),
                    LocalDate.of(2026, 12, 22))
                .slackLine())
        .isEqualTo(
            "R17 ja R21 puuduvad (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-22):"
                + " lae EPIS-ist");
  }

  @Test
  void aSingleMissingPevaRavaReportIsNamedAlone() {
    assertThat(
            new MissingPevaRavaReports(
                    List.of(R17_PEVA), LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 14))
                .slackLine())
        .isEqualTo(
            "R17 puudub (PEVA/RAVA täitmine 2027-01-04, vood arvesse alates 2026-12-14):"
                + " lae EPIS-ist");
  }

  @Test
  void onlyPevaRavaReportTypesCanBeMissingFromACycle() {
    var failure =
        new MissingPevaRavaReports(
            List.of(R45), LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 14));

    assertThatThrownBy(failure::slackLine).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void instrumentsWithoutAPriceAreListedOnOneLine() {
    assertThat(new MissingPrices(List.of("IE00BFG1TM61", "LU0476289540")).slackLine())
        .isEqualTo(
            "Hind puudub: IE00BFG1TM61, LU0476289540: kontrolli hinnaallikaid ja"
                + " instrument_reference'it");
  }

  @Test
  void aHoldingTheRegistryAndSebDisagreeOnShowsBothQuantitiesWithoutTrailingZeros() {
    assertThat(
            new HoldingMismatch(
                    "IE00BFG1TM61",
                    LocalDate.of(2026, 11, 17),
                    new BigDecimal("1000.0000"),
                    new BigDecimal("990.500000"))
                .slackLine())
        .isEqualTo(
            "IE00BFG1TM61: registris 1000, SEB raportis 990.5 (2026-11-17): kontrolli registri"
                + " tehinguid");
  }

  @Test
  void aHoldingOneSideDoesNotListSaysItIsMissingThereInsteadOfShowingZero() {
    assertThat(
            new HoldingMismatch(
                    "IE00BFG1TM61", LocalDate.of(2026, 11, 17), new BigDecimal("5.0000"), null)
                .slackLine())
        .isEqualTo(
            "IE00BFG1TM61: registris 5, SEB raportis puudub (2026-11-17): kontrolli registri"
                + " tehinguid");
    assertThat(
            new HoldingMismatch(
                    "IE00BFG1TM61", LocalDate.of(2026, 11, 17), null, new BigDecimal("40"))
                .slackLine())
        .isEqualTo(
            "IE00BFG1TM61: registris puudub, SEB raportis 40 (2026-11-17): kontrolli registri"
                + " tehinguid");
  }

  @Test
  void noSameDayCashSaysWhenTheBalanceWasLastFetchedAndHowToGoOn() {
    assertThat(new NoSameDayCash(Instant.parse("2026-11-19T14:50:00Z")).slackLine())
        .isEqualTo(
            "SEB-st pole tänast saldot (viimane 2026-11-19 16:50): kontrolli SEB päringuid või"
                + " anna raha käsitsi (cash ja kommentaar)");
    assertThat(new NoSameDayCash(null).slackLine())
        .isEqualTo(
            "SEB-st pole tänast saldot: kontrolli SEB päringuid või anna raha käsitsi (cash ja"
                + " kommentaar)");
    assertThat(new UnreadableSameDayCash().slackLine())
        .isEqualTo(
            "SEB saldot ei saanud lugeda: kontrolli SEB sõnumeid või anna raha käsitsi (cash ja"
                + " kommentaar)");
  }

  @Test
  void noCommonPriceDateSaysWhichDaysWereSearchedAndWhatToDo() {
    assertThat(
            new NoCommonPriceDate(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 17))
                .slackLine())
        .isEqualTo(
            "Ühist hinnapäeva pole (2026-11-03–2026-11-17): kontrolli hinnaallikaid või kasuta"
                + " igaühe värskeimat hinda");
  }

  @Test
  void aRegistryWithoutHoldingsSaysWhereTheyComeFrom() {
    assertThat(new NoRegistryHoldings(LocalDate.of(2026, 11, 17)).slackLine())
        .isEqualTo(
            "Registris pole 2026-11-17 positsioone: kontrolli portfelli baseline'i ja cost-basis"
                + " tööd");
  }

  @Test
  void securitiesTheSebReportGivesNoQuantityForAreNamed() {
    assertThat(
            new UnquantifiedReportHoldings(
                    LocalDate.of(2026, 11, 17), List.of("IE00BFG1TM61", "LU0476289540"))
                .slackLine())
        .isEqualTo(
            "SEB raportis pole kogust (2026-11-17): IE00BFG1TM61, LU0476289540: kontrolli raporti"
                + " importi");
  }

  @Test
  void aSebReportWithoutSecuritiesSaysToCheckItsImport() {
    assertThat(new NoReportSecurities(LocalDate.of(2026, 11, 17)).slackLine())
        .isEqualTo("SEB raportis pole 2026-11-17 väärtpabereid: kontrolli raporti importi");
  }

  @Test
  void aMissingR16NamesTheMonthItsPaymentDeadlineAndWhenTradePrepCountsIt() {
    assertThat(
            new MissingR16(
                    YearMonth.of(2026, 11), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9))
                .slackLine())
        .isEqualTo(
            "R16 2026-11 puudub (väljamaksed 2026-11-16, arvesse alates 2026-11-09): lae R16"
                + " EPIS-ist");
  }
}
