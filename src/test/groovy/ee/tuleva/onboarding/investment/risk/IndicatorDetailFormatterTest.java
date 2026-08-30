package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_CONFIRMED;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.CHANGE_PENDING;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IndicatorDetailFormatterTest {

  private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 7, 31);

  private final DisclosedRiskIndicatorRepository disclosures =
      Mockito.mock(DisclosedRiskIndicatorRepository.class);
  private final Clock clock =
      Clock.fixed(
          LocalDate.of(2026, 8, 6).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
  private final List<DisclosedRiskIndicator> disclosureRows = new ArrayList<>();

  private final IndicatorDetailFormatter detailFormatter =
      new IndicatorDetailFormatter(
          disclosures, clock, new IndicatorDiagnosticsFormatter(), new RiskClassRangeFormatter());

  @BeforeEach
  void setUp() {
    given(
            disclosures
                .findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
                    any(), any(), any()))
        .willAnswer(
            invocation ->
                documentInForce(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
  }

  @Test
  void aTruncatedPublishedSinceIsPrintedAsALowerBoundInTheTableAndExplainedInTheBlock() {
    var indicator =
        new PublishedRiskIndicator(
            TKF100,
            SRI,
            EVALUATION_DATE,
            4,
            4,
            null,
            LocalDate.of(2026, 3, 14),
            true,
            LocalDate.of(2026, 3, 14),
            85,
            85,
            85,
            85,
            1305,
            new BigDecimal("0.154000000000"),
            STABLE);
    disclose(SRI, TKF100, 4);

    assertThat(detailFormatter.publishedSince(indicator)).isEqualTo("≥2026-03-14");
    assertThat(detailFormatter.detailBlock(outcome(indicator)))
        .contains("on alampiir, mitte tegelik algus — jooksuta RiskIndicatorBackfillJob.");
  }

  @Test
  void durationIsPrintedInMonthsUnderAYearAndInYearsAndMonthsFromExactlyTwelveMonths() {
    var indicator = withPublishedSince(stableSri(), EVALUATION_DATE.minusMonths(6));
    var oneYear = withPublishedSince(stableSri(), EVALUATION_DATE.minusMonths(12));

    assertThat(detailFormatter.duration(indicator)).isEqualTo("6 kuud");
    assertThat(detailFormatter.duration(oneYear)).isEqualTo("1a 0k");
  }

  @Test
  void aShortSrriWindowRaisesADataQualityLine() {
    var indicator =
        new PublishedRiskIndicator(
            TUK75,
            SRRI,
            EVALUATION_DATE,
            4,
            4,
            null,
            LocalDate.of(2021, 9, 6),
            false,
            LocalDate.of(2021, 9, 6),
            250,
            250,
            17,
            17,
            259,
            new BigDecimal("0.0930"),
            STABLE);
    disclose(SRRI, TUK75, 4);

    assertThat(detailFormatter.detailBlock(outcome(indicator)))
        .contains("⚠️ TUK75 SRRI: 5a aknas 259/260 nädalatootlust");
  }

  @Test
  void theOpenEndedTopClassPrintsItsRangeWithoutAnUpperBound() {
    disclose(SRRI, TUV100, 7);

    assertThat(detailFormatter.detailBlock(outcome(topClassSrri())))
        .contains("(klassi 7 vahemik 25,00%–∞); lähim piir on 5,00% kaugusel.");
  }

  @Test
  void aClassWithoutAVolatilityStillPrintsItsRange() {
    disclose(SRI, TKF100, 4);

    assertThat(detailFormatter.detailBlock(outcome(withVolatility(stableSri(), null))))
        .contains("Klassi 4 vahemik 0,1200–0,2000.");
  }

  @Test
  void aConfirmedChangeThatTheDocumentAlreadyReflectsIsSpelledOutRatherThanFlaggedRed() {
    disclose(SRRI, TUV100, 5);

    assertThat(detailFormatter.detailBlock(outcome(staleDocumentSrri())))
        .contains(
            "⚠️ TUV100 SRRI — muutus äsja kinnitatud",
            "Avaldatav klass 5 alates 2026-05-05 (eelmine 4).",
            "👉 Tegevus: kontrolli, kas dokument on juba uuendatud.");
  }

  @Test
  void aPendingSriChangeCountsTheReferencePointsStillMissingForTheMajority() {
    disclose(SRI, TKF100, 4);

    assertThat(detailFormatter.detailBlock(outcome(pendingSri())))
        .contains(
            "Klass 5 on hoidnud 30 kauplemispäeva alates 2026-06-15."
                + " PRIIPs enamuseni puudu 13 punkti.");
  }

  @Test
  void thePendingSrriLineCountsTheWindowPointsThatStillBlockTheMigration() {
    disclose(SRRI, TUK75, 4);

    assertThat(detailFormatter.detailBlock(outcome(pendingSrri())))
        .contains("eeldatav kinnitus 2026-09-25; aknas on veel 8 referentspunkti muus klassis.");
  }

  @Test
  void aPendingSrriChangeWithoutAKnownStartDateOnlyReportsTheStreak() {
    disclose(SRRI, TUK75, 4);

    assertThat(detailFormatter.detailBlock(outcome(withRawClassSince(pendingSrri(), null))))
        .contains("Klass 5 on hoidnud 9 nädalat.");
  }

  @Test
  void recomputedHistoricalPointsAreCalledOutInTheBlock() {
    disclose(SRI, TKF100, 4);
    var outcome =
        new RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            List.of(),
            List.of());

    assertThat(detailFormatter.detailBlock(outcome))
        .contains(
            "⚠️ TKF100 SRI: 2 varasemat referentspunkti arvutati ümber",
            "vanim 2026-06-01, viimane 2026-06-30");
  }

  @Test
  void aChangedHoldingPeriodIsReportedAsARedefinitionRatherThanSourceDataDrift() {
    disclose(SRI, TKF100, 4);
    var outcome =
        new RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(),
            List.of(
                new Redefinition(LocalDate.of(2026, 6, 1), "1300", "1280"),
                new Redefinition(LocalDate.of(2026, 6, 30), "1300", "1280")),
            List.of());

    assertThat(detailFormatter.detailBlock(outcome))
        .contains(
            "ℹ️ TKF100 SRI: 2 varasemat referentspunkti arvutati ümber",
            "vanim 2026-06-01, viimane 2026-06-30",
            "hoidmisperioodi kauplemispäevi 1300 → 1280")
        .doesNotContain("allikaandmed muutusid tagantjärele");
  }

  @Test
  void referencePointsTheCalculatorCouldNotUseAreCalledOutInTheBlock() {
    disclose(SRI, TKF100, 4);
    var outcome =
        new RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(),
            List.of(),
            List.of(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 7, 1)));

    assertThat(detailFormatter.detailBlock(outcome))
        .contains(
            "⚠️ TKF100 SRI: 2 referentspunkti jäi arvutamata",
            "vanim 2026-06-02, viimane 2026-07-01");
  }

  @Test
  void aFundWithoutEnoughDataGetsAnAndmedNapibBlock() {
    var indicator =
        PublishedRiskIndicator.insufficientData(
            TUK00, SRRI, EVALUATION_DATE, 17, new BigDecimal("0.021000000000"));

    assertThat(detailFormatter.detailBlock(outcome(indicator)))
        .contains(
            "⚠️ TUK00 SRRI — andmeid napib",
            "Aknas on 17 vaatlust, klassi ei avaldata. Volatiilsus 2,10%.",
            "👉 Tegevus: kontrolli, kas NAV-seeria on täielik. Kui fondi enda ajalugu ongi nõutud"
                + " perioodist lühem, lisa investment.risk.sources alla võrdlusindeksi segment —"
                + " klassi avaldamata jätmine ei ole lubatud variant.");
  }

  @Test
  void aMismatchedDocumentIsFlaggedRed() {
    disclose(SRRI, TUV100, 4);

    assertThat(detailFormatter.detailBlock(outcome(staleDocumentSrri())))
        .contains(
            "🔴 TUV100 SRRI — dokumendis on klass 4, arvutatud avaldatav klass on 5",
            "Muutus jõustus 2026-05-05. Viimane dokument: 'Pohiteave TUV100' (klass 4, alates"
                + " 2026-03-19).",
            "👉 Tegevus: dokument vajab uuendamist. Pärast avaldamist lisa rida"
                + " investment_risk_indicator_disclosure tabelisse.");
  }

  @Test
  void anUndisclosedIndicatorAsksForTheDocumentRow() {
    assertThat(detailFormatter.detailBlock(outcome(undisclosedSrri())))
        .contains(
            "⚠️ TUK00 SRRI — avaldatud klass teadmata",
            "Arvutatud avaldatav klass on 4, aga ühtegi dokumendirida ei ole.",
            "👉 Tegevus: lisa kehtiv KID/KIID rida investment_risk_indicator_disclosure"
                + " tabelisse.");
  }

  @Test
  void aStableSriIndicatorReportsThePriipsMajority() {
    disclose(SRI, TKF100, 4);

    assertThat(detailFormatter.detailBlock(outcome(stableSri())))
        .contains(
            "✅ TKF100 SRI — stabiilne, dokument ajakohane",
            "PRIIPs 4-kuu enamus: 85/85 referentspunkti klassile 4; pöördeks on vaja 43"
                + " vastupidist referentspunkti.");
  }

  @Test
  void aStableSrriIndicatorReportsTheCesrMajority() {
    disclose(SRRI, TUK00, 4);

    assertThat(detailFormatter.detailBlock(outcome(undisclosedSrri())))
        .contains(
            "CESR 4-kuu aken: 17/17 nädalat klassile 4; migratsiooniks peab volatiilsus olema"
                + " kõik neli kuud väljaspool avaldatavat klassi.");
  }

  @Test
  void statusLabelReflectsStablePendingInsufficientAndUndisclosedIndicators() {
    disclose(SRI, TKF100, 4);

    var stable = stableSri();
    var pending = pendingSri();
    var insufficient =
        PublishedRiskIndicator.insufficientData(
            TUK00, SRRI, EVALUATION_DATE, 17, new BigDecimal("0.021000000000"));
    var undisclosed = undisclosedSrri();

    assertThat(detailFormatter.statusLabel(stable, detailFormatter.disclosedClass(stable)))
        .isEqualTo("✅ stabiilne");
    assertThat(detailFormatter.statusLabel(pending, detailFormatter.disclosedClass(pending)))
        .isEqualTo("⚠️ muutus ootel");
    assertThat(
            detailFormatter.statusLabel(insufficient, detailFormatter.disclosedClass(insufficient)))
        .isEqualTo("⚠️ andmeid napib");
    assertThat(
            detailFormatter.statusLabel(undisclosed, detailFormatter.disclosedClass(undisclosed)))
        .isEqualTo("⚠️ avaldatud teadmata");
  }

  @Test
  void statusLabelReflectsAConfirmedChangeTheDocumentAlreadyMatches() {
    disclose(SRRI, TUV100, 5);
    var confirmed = staleDocumentSrri();

    assertThat(detailFormatter.statusLabel(confirmed, detailFormatter.disclosedClass(confirmed)))
        .isEqualTo("⚠️ muutus kinnitatud");
  }

  @Test
  void statusLabelReflectsAMismatchedDocument() {
    disclose(SRRI, TUV100, 4);
    var mismatch = staleDocumentSrri();

    assertThat(detailFormatter.statusLabel(mismatch, detailFormatter.disclosedClass(mismatch)))
        .isEqualTo("🔴 DOKUMENT VANANENUD");
  }

  @Test
  void severityIsRedOnMismatchYellowWhenUndecidedAndGreenWhenStable() {
    disclose(SRI, TKF100, 4);
    disclose(SRRI, TUV100, 4);

    var stable = stableSri();
    var mismatch = staleDocumentSrri();
    var undisclosed = undisclosedSrri();

    assertThat(detailFormatter.severity(stable, detailFormatter.disclosedClass(stable)))
        .isEqualTo(Severity.GREEN);
    assertThat(detailFormatter.severity(mismatch, detailFormatter.disclosedClass(mismatch)))
        .isEqualTo(Severity.RED);
    assertThat(detailFormatter.severity(undisclosed, detailFormatter.disclosedClass(undisclosed)))
        .isEqualTo(Severity.YELLOW);
  }

  @Test
  void isMismatchedComparesTheDisclosedClassAgainstThePublishedClass() {
    disclose(SRRI, TUV100, 4);
    var mismatch = staleDocumentSrri();
    var disclosed = detailFormatter.disclosedClass(mismatch);

    assertThat(detailFormatter.isMismatched(disclosed, mismatch)).isTrue();
    assertThat(detailFormatter.isMismatched(null, mismatch)).isFalse();
  }

  @Test
  void mismatchLineNamesBothTheDocumentedAndComputedClass() {
    disclose(SRRI, TUV100, 4);
    var mismatch = staleDocumentSrri();
    var disclosed = detailFormatter.disclosedClass(mismatch);

    assertThat(detailFormatter.mismatchLine(mismatch, disclosed))
        .isEqualTo("🔴 TUV100 SRRI — dokumendis on klass 4, arvutatud avaldatav klass on 5");
  }

  @Test
  void disclosedClassLooksUpTheDocumentInForceAsOfToday() {
    disclose(SRI, TKF100, 4);

    var disclosed = detailFormatter.disclosedClass(stableSri());

    assertThat(disclosed).isNotNull();
    assertThat(disclosed.getDisclosedClass()).isEqualTo(4);
  }

  private PublishedRiskIndicator topClassSrri() {
    return new PublishedRiskIndicator(
        TUV100,
        SRRI,
        EVALUATION_DATE,
        7,
        7,
        6,
        LocalDate.of(2025, 5, 5),
        false,
        LocalDate.of(2025, 5, 5),
        60,
        60,
        17,
        17,
        260,
        new BigDecimal("0.300000000000"),
        STABLE);
  }

  private PublishedRiskIndicator pendingSri() {
    return new PublishedRiskIndicator(
        TKF100,
        SRI,
        EVALUATION_DATE,
        4,
        5,
        4,
        LocalDate.of(2026, 3, 14),
        false,
        LocalDate.of(2026, 6, 15),
        85,
        30,
        85,
        30,
        1305,
        new BigDecimal("0.204000000000"),
        CHANGE_PENDING);
  }

  private PublishedRiskIndicator withVolatility(
      PublishedRiskIndicator indicator, @Nullable BigDecimal volatility) {
    return new PublishedRiskIndicator(
        indicator.fund(),
        indicator.indicatorType(),
        indicator.evaluationDate(),
        indicator.publishedClass(),
        indicator.rawLatestClass(),
        indicator.previousPublishedClass(),
        indicator.publishedSince(),
        indicator.publishedSinceIsTruncated(),
        indicator.rawClassSince(),
        indicator.streakReferencePoints(),
        indicator.rawStreakReferencePoints(),
        indicator.windowReferencePoints(),
        indicator.matchingReferencePoints(),
        indicator.latestObservationCount(),
        volatility,
        indicator.status());
  }

  private PublishedRiskIndicator withRawClassSince(
      PublishedRiskIndicator indicator, @Nullable LocalDate rawClassSince) {
    return new PublishedRiskIndicator(
        indicator.fund(),
        indicator.indicatorType(),
        indicator.evaluationDate(),
        indicator.publishedClass(),
        indicator.rawLatestClass(),
        indicator.previousPublishedClass(),
        indicator.publishedSince(),
        indicator.publishedSinceIsTruncated(),
        rawClassSince,
        indicator.streakReferencePoints(),
        indicator.rawStreakReferencePoints(),
        indicator.windowReferencePoints(),
        indicator.matchingReferencePoints(),
        indicator.latestObservationCount(),
        indicator.latestVolatility(),
        indicator.status());
  }

  private PublishedRiskIndicator withPublishedSince(
      PublishedRiskIndicator indicator, LocalDate publishedSince) {
    return new PublishedRiskIndicator(
        indicator.fund(),
        indicator.indicatorType(),
        indicator.evaluationDate(),
        indicator.publishedClass(),
        indicator.rawLatestClass(),
        indicator.previousPublishedClass(),
        publishedSince,
        indicator.publishedSinceIsTruncated(),
        indicator.rawClassSince(),
        indicator.streakReferencePoints(),
        indicator.rawStreakReferencePoints(),
        indicator.windowReferencePoints(),
        indicator.matchingReferencePoints(),
        indicator.latestObservationCount(),
        indicator.latestVolatility(),
        indicator.status());
  }

  private PublishedRiskIndicator stableSri() {
    return new PublishedRiskIndicator(
        TKF100,
        SRI,
        EVALUATION_DATE,
        4,
        4,
        null,
        LocalDate.of(2026, 3, 14),
        false,
        LocalDate.of(2026, 3, 14),
        85,
        85,
        85,
        85,
        1305,
        new BigDecimal("0.154000000000"),
        STABLE);
  }

  private PublishedRiskIndicator pendingSrri() {
    return new PublishedRiskIndicator(
        TUK75,
        SRRI,
        EVALUATION_DATE,
        4,
        5,
        5,
        LocalDate.of(2021, 9, 6),
        false,
        LocalDate.of(2026, 5, 25),
        250,
        9,
        17,
        9,
        260,
        new BigDecimal("0.153000000000"),
        CHANGE_PENDING);
  }

  private PublishedRiskIndicator staleDocumentSrri() {
    return new PublishedRiskIndicator(
        TUV100,
        SRRI,
        EVALUATION_DATE,
        5,
        5,
        4,
        LocalDate.of(2026, 5, 5),
        false,
        LocalDate.of(2026, 5, 5),
        13,
        13,
        17,
        17,
        260,
        new BigDecimal("0.163000000000"),
        CHANGE_CONFIRMED);
  }

  private PublishedRiskIndicator undisclosedSrri() {
    return new PublishedRiskIndicator(
        TUK00,
        SRRI,
        EVALUATION_DATE,
        4,
        4,
        3,
        LocalDate.of(2022, 6, 27),
        false,
        LocalDate.of(2022, 6, 27),
        213,
        213,
        17,
        17,
        260,
        new BigDecimal("0.058000000000"),
        STABLE);
  }

  private void disclose(RiskIndicatorType type, TulevaFund fund, int disclosedClass) {
    discloseFrom(type, fund, disclosedClass, LocalDate.of(2026, 3, 19));
  }

  private void discloseFrom(
      RiskIndicatorType type, TulevaFund fund, int disclosedClass, LocalDate disclosedFrom) {
    disclosureRows.add(
        DisclosedRiskIndicator.builder()
            .indicatorType(type)
            .fund(fund)
            .disclosedClass(disclosedClass)
            .disclosedFrom(disclosedFrom)
            .document("Pohiteave " + fund)
            .build());
  }

  private Optional<DisclosedRiskIndicator> documentInForce(
      RiskIndicatorType type, TulevaFund fund, LocalDate asOf) {
    return disclosureRows.stream()
        .filter(row -> row.getIndicatorType() == type && row.getFund() == fund)
        .filter(row -> !row.getDisclosedFrom().isAfter(asOf))
        .max(Comparator.comparing(DisclosedRiskIndicator::getDisclosedFrom));
  }

  private RiskIndicatorOutcome outcome(PublishedRiskIndicator indicator) {
    return new RiskIndicatorOutcome(
        indicator,
        null,
        RiskIndicatorPublication.builder().build(),
        List.of(),
        List.of(),
        List.of());
  }
}
