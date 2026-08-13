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

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties.ProxyReview;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties.Source;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.PublicationSnapshot;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class RiskIndicatorNotifierTest {

  private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 7, 31);
  private static final LocalDate FOURTH_BUSINESS_DAY = LocalDate.of(2026, 8, 6);
  private static final LocalDate DIGEST_MONTH = LocalDate.of(2026, 8, 1);

  private final RecordingNotificationService notifications = new RecordingNotificationService();
  private final DisclosedRiskIndicatorRepository disclosures =
      Mockito.mock(DisclosedRiskIndicatorRepository.class);
  private final RiskIndicatorDigestRepository digests =
      Mockito.mock(RiskIndicatorDigestRepository.class);
  private final FundValueRepository fundValues = Mockito.mock(FundValueRepository.class);
  private final RiskIndicatorPublicationRepository publications =
      Mockito.mock(RiskIndicatorPublicationRepository.class);
  private final Map<TulevaFund, ProxyReview> proxyReviews = new HashMap<>();
  private final Map<LocalDate, RiskIndicatorDigest> digestRows = new HashMap<>();
  private final List<DisclosedRiskIndicator> disclosureRows = new ArrayList<>();

  private RiskIndicatorNotifier notifier;

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
    given(digests.findByDigestMonth(any()))
        .willAnswer(
            invocation -> Optional.ofNullable(detached(digestRows.get(invocation.getArgument(0)))));
    given(digests.save(any())).willAnswer(invocation -> merge(invocation.getArgument(0)));
    given(fundValues.findEarliestDateForKey(any())).willReturn(Optional.empty());
    notifier = notifier(FOURTH_BUSINESS_DAY);
  }

  private RiskIndicatorDigest merge(RiskIndicatorDigest incoming) {
    var row = digestRows.get(incoming.getDigestMonth());
    if (row != null && !Objects.equals(row.getVersion(), incoming.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(
          RiskIndicatorDigest.class, incoming.getDigestMonth());
    }
    var merged =
        RiskIndicatorDigest.builder()
            .id(1L)
            .digestMonth(incoming.getDigestMonth())
            .complete(incoming.getComplete())
            .version(row == null ? 0L : row.getVersion() + 1)
            .build();
    digestRows.put(merged.getDigestMonth(), merged);
    return detached(merged);
  }

  private @Nullable RiskIndicatorDigest detached(@Nullable RiskIndicatorDigest row) {
    return row == null
        ? null
        : RiskIndicatorDigest.builder()
            .id(row.getId())
            .digestMonth(row.getDigestMonth())
            .complete(row.getComplete())
            .version(row.getVersion())
            .build();
  }

  private void storeDigestRow(boolean complete) {
    digestRows.put(
        DIGEST_MONTH,
        RiskIndicatorDigest.builder()
            .id(1L)
            .digestMonth(DIGEST_MONTH)
            .complete(complete)
            .version(0L)
            .build());
  }

  private RiskIndicatorDigest storedDigestRow() {
    return digestRows.get(DIGEST_MONTH);
  }

  private RiskIndicatorNotifier notifier(LocalDate today) {
    var properties =
        new RiskIndicatorProperties(
            Map.of(
                TKF100, List.of(new Source("MSCI_ACWI", null)),
                TUK75, List.of(new Source(TUK75.getIsin(), null)),
                TUK00, List.of(new Source(TUK00.getIsin(), null)),
                TUV100, List.of(new Source(TUV100.getIsin(), null))),
            proxyReviews);
    return new RiskIndicatorNotifier(
        notifications,
        disclosures,
        digests,
        publications,
        properties,
        fundValues,
        new BusinessDays(new PublicHolidays()),
        Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
  }

  @Test
  void theDigestCoversEveryFundAndNamesTheActionForEachOne() {
    disclose(SRI, TKF100, 4);
    disclose(SRRI, TUK75, 4);
    disclose(SRRI, TUV100, 4);

    notifier.notify(run(stableSri(), pendingSrri(), staleDocumentSrri(), undisclosedSrri()));

    var digest = notifications.lastMessage();
    assertThat(digest)
        .isEqualTo(
            """
            🔴 Riskiindikaatorite kuuülevaade — seisuga 2026-07-31
            1 vajab tegevust, 2 jälgimist, 1 korras.

            ```
            Fond    Näitaja Arvutatud  Avaldatud  Kehtib alates  Kestus   Eelmine  Staatus
            TKF100  SRI     4          4          2026-03-14     4 kuud   —        ✅ stabiilne
            TUK75   SRRI    4          4          2021-09-06     4a 10k   5        ⚠️ muutus ootel
            TUV100  SRRI    5          4          2026-05-05     2 kuud   4        🔴 DOKUMENT VANANENUD
            TUK00   SRRI    4          ?          2022-06-27     4a 1k    3        ⚠️ avaldatud teadmata
            ```

            ✅ TKF100 SRI — stabiilne, dokument ajakohane
            VEV 0,1540 (klassi 4 vahemik 0,1200–0,2000); lähim piir on 0,0340 kaugusel.
            PRIIPs 4-kuu enamus: 85/85 referentspunkti klassile 4; pöördeks on vaja 43 vastupidist referentspunkti.

            ⚠️ TUK75 SRRI — muutus ootel
            Toores klass 5, avaldatav klass 4. Aastane volatiilsus 15,30%.
            Klass 5 on püsinud 9 nädalat alates 2026-05-25. CESR 4-kuu künniseni puudu 9 nädalat, eeldatav kinnitus 2026-09-25; aknas on veel 8 referentspunkti muus klassis.
            👉 Tegevus praegu pole — jälgi.

            🔴 TUV100 SRRI — dokumendis on klass 4, arvutatud avaldatav klass on 5
            Muutus jõustus 2026-05-05. Viimane dokument: 'Pohiteave TUV100' (klass 4, alates 2026-03-19).
            👉 Tegevus: dokument vajab uuendamist. Pärast avaldamist lisa rida investment_risk_indicator_disclosure tabelisse.

            ⚠️ TUK00 SRRI — avaldatud klass teadmata
            Arvutatud avaldatav klass on 4, aga ühtegi dokumendirida ei ole.
            👉 Tegevus: lisa kehtiv KID/KIID rida investment_risk_indicator_disclosure tabelisse.

            Allikad: TKF100: MSCI_ACWI; TUK75: EE3600109435; TUV100: EE3600001707; TUK00: EE3600109443. SRI = MRM, eeldusel CRM = 1.
            """);
  }

  @Test
  void theDigestIsSentOnceAMonthAndRecorded() {
    notifier.notify(run(stableSri()));

    assertThat(notifications.messages).hasSize(1);
    Mockito.verify(digests)
        .save(RiskIndicatorDigest.builder().digestMonth(LocalDate.of(2026, 8, 1)).build());
  }

  @Test
  void theDigestIsNotSentBeforeTheFourthBusinessDay() {
    notifier(LocalDate.of(2026, 8, 5)).notify(run(stableSri()));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void aLateDigestStillGoesOutOnTheFifthBusinessDay() {
    notifier(LocalDate.of(2026, 8, 7)).notify(run(stableSri()));

    assertThat(notifications.messages).hasSize(1);
  }

  @Test
  void theDigestIsNotSentTwiceInTheSameMonth() {
    storeDigestRow(true);

    notifier.notify(run(stableSri()));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void aFreshlyMistypedDisclosureRowStillRaisesTheRedAlert() {
    disclose(SRRI, TUV100, 4);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    new RiskIndicatorOutcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(
                            EVALUATION_DATE.minusDays(1), 5, 5, CHANGE_CONFIRMED),
                        RiskIndicatorPublication.builder().build(),
                        List.of())),
                List.of()));

    assertThat(notifications.messages)
        .singleElement()
        .asString()
        .contains("🔴 TUV100 SRRI — dokumendis on klass 4, arvutatud avaldatav klass on 5");
  }

  @Test
  void aDocumentCorrectedAfterTheLatestReferencePointIsAlreadyInForceToday() {
    disclose(SRRI, TUV100, 4);
    discloseFrom(SRRI, TUV100, 5, LocalDate.of(2026, 8, 3));

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    outcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(
                            EVALUATION_DATE.minusDays(1), 5, 5, CHANGE_CONFIRMED))),
                List.of()));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void aMismatchRetypedIntoADifferentWrongClassIsAlertedAgain() {
    disclose(SRRI, TUV100, 6);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    outcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(
                            EVALUATION_DATE.minusDays(1), 5, 4, CHANGE_CONFIRMED))),
                List.of()));

    assertThat(notifications.messages)
        .containsExactly(
            """
            Riskiindikaatori muutus
            🔴 TUV100 SRRI — dokumendis on klass 6, arvutatud avaldatav klass on 5""");
  }

  @Test
  void anAlreadyReportedMismatchIsNotRepeatedEveryDay() {
    disclose(SRRI, TUV100, 4);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    new RiskIndicatorOutcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(
                            EVALUATION_DATE.minusDays(1), 5, 4, CHANGE_CONFIRMED),
                        RiskIndicatorPublication.builder().build(),
                        List.of())),
                List.of()));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void markingNotifiedRecordsWhatTheDocumentSaidAtThatMoment() {
    disclose(SRRI, TUV100, 4);
    var publication = RiskIndicatorPublication.builder().build();

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    new RiskIndicatorOutcome(staleDocumentSrri(), null, publication, List.of())),
                List.of()));

    assertThat(publication.getNotifiedDisclosedClass()).isEqualTo(4);
  }

  @Test
  void aTruncatedPublishedSinceIsPrintedAsALowerBound() {
    var indicator = stableSri();
    var truncated =
        new PublishedRiskIndicator(
            indicator.fund(),
            indicator.indicatorType(),
            indicator.evaluationDate(),
            indicator.publishedClass(),
            indicator.rawLatestClass(),
            indicator.previousPublishedClass(),
            indicator.publishedSince(),
            true,
            indicator.rawClassSince(),
            indicator.streakReferencePoints(),
            indicator.rawStreakReferencePoints(),
            indicator.windowReferencePoints(),
            indicator.matchingReferencePoints(),
            indicator.latestObservationCount(),
            indicator.latestVolatility(),
            indicator.status());
    disclose(SRI, TKF100, 4);

    notifier.notify(run(truncated));

    assertThat(notifications.lastMessage())
        .contains(
            "≥2026-03-14", "on alampiir, mitte tegelik algus — jooksuta RiskIndicatorBackfillJob.");
  }

  @Test
  void aRunWhereEveryDocumentAgreesLeadsWithGreen() {
    disclose(SRI, TKF100, 4);
    disclose(SRRI, TUK00, 4);

    notifier.notify(run(stableSri(), undisclosedSrri()));

    assertThat(notifications.lastMessage())
        .startsWith("✅ Riskiindikaatorite kuuülevaade — seisuga 2026-07-31\n2 korras.");
  }

  @Test
  void aPendingChangeLeadsWithYellowNotRed() {
    disclose(SRRI, TUK75, 4);

    notifier.notify(run(pendingSrri()));

    assertThat(notifications.lastMessage())
        .startsWith("⚠️ Riskiindikaatorite kuuülevaade — seisuga 2026-07-31\n1 jälgimist.");
  }

  @Test
  void aStaleDocumentLeadsWithRedEvenWhenEverythingElseIsFine() {
    disclose(SRI, TKF100, 4);
    disclose(SRRI, TUV100, 4);

    notifier.notify(run(stableSri(), staleDocumentSrri()));

    assertThat(notifications.lastMessage())
        .startsWith(
            "🔴 Riskiindikaatorite kuuülevaade — seisuga 2026-07-31\n1 vajab tegevust, 1 korras.");
  }

  @Test
  void anUnevaluatedFundIsCountedInTheHeadline() {
    disclose(SRI, TKF100, 4);

    notifier.notify(
        new RiskIndicatorRun(
            EVALUATION_DATE, List.of(outcome(stableSri(), null)), List.of("TUK75 SRRI: no data")));

    assertThat(notifications.lastMessage())
        .startsWith(
            "⚠️ Riskiindikaatorite kuuülevaade — seisuga 2026-07-31\n1 korras, 1 hindamata.");
  }

  @Test
  void anIncompleteDigestIsSentButLeavesTheMonthOpenForACompleteOne() {
    var withFailure =
        new RiskIndicatorRun(
            EVALUATION_DATE, List.of(outcome(stableSri(), null)), List.of("TUK75 SRRI: no data"));

    notifier.notify(withFailure);

    Mockito.verify(digests)
        .save(
            RiskIndicatorDigest.builder()
                .digestMonth(LocalDate.of(2026, 8, 1))
                .complete(false)
                .build());
  }

  @Test
  void aCompleteRunResendsTheDigestOverAnIncompleteMonth() {
    storeDigestRow(false);

    notifier.notify(run(stableSri()));

    assertThat(notifications.messages).hasSize(1);
    assertThat(storedDigestRow())
        .isEqualTo(
            RiskIndicatorDigest.builder()
                .id(1L)
                .digestMonth(DIGEST_MONTH)
                .complete(true)
                .version(1L)
                .build());
  }

  @Test
  void anIncompleteMonthIsNotResentWhileTheSameFundKeepsFailing() {
    storeDigestRow(false);

    notifier.notify(
        new RiskIndicatorRun(
            EVALUATION_DATE, List.of(outcome(stableSri(), null)), List.of("TUK75 SRRI: no data")));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void aFailedSendReleasesTheMonthSoTheNextDayRetries() {
    notifications.failing = true;

    notifier.notify(run(stableSri()));

    Mockito.verify(digests).delete(any());
  }

  @Test
  void theTransitionBaselineOnlyAdvancesOnceTheMessageIsOut() {
    var publication = RiskIndicatorPublication.builder().build();
    var run =
        new RiskIndicatorRun(
            EVALUATION_DATE,
            List.of(new RiskIndicatorOutcome(stableSri(), null, publication, List.of())),
            List.of());

    notifier.notify(run);

    assertThat(publication.getNotified()).isTrue();
  }

  @Test
  void aFailedTransitionSendLeavesTheBaselineWhereItWas() {
    disclose(SRRI, TUV100, 4);
    notifications.failing = true;
    var publication = RiskIndicatorPublication.builder().build();

    notifier.notify(
        new RiskIndicatorRun(
            EVALUATION_DATE,
            List.of(new RiskIndicatorOutcome(staleDocumentSrri(), null, publication, List.of())),
            List.of()));

    assertThat(publication.getNotified()).isFalse();
  }

  @Test
  void recomputedHistoricalPointsAreCalledOutInTheDigest() {
    disclose(SRI, TKF100, 4);

    notifier.notify(
        new RiskIndicatorRun(
            EVALUATION_DATE,
            List.of(
                new RiskIndicatorOutcome(
                    stableSri(),
                    null,
                    RiskIndicatorPublication.builder().build(),
                    List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))),
            List.of()));

    assertThat(notifications.lastMessage())
        .contains(
            "⚠️ TKF100 SRI: 2 varasemat referentspunkti arvutati ümber",
            "vanim 2026-06-01, viimane 2026-06-30");
  }

  @Test
  void aColdStartSuppressesTheTransitionAlertButNotTheDocumentMismatch() {
    disclose(SRRI, TUV100, 4);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE, List.of(outcome(staleDocumentSrri(), null)), List.of()));

    assertThat(notifications.messages)
        .containsExactly(
            """
            Riskiindikaatori muutus
            🔴 TUV100 SRRI — dokumendis on klass 4, arvutatud avaldatav klass on 5""");
  }

  @Test
  void aPublishedClassChangeIsAlertedImmediately() {
    disclose(SRRI, TUV100, 5);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    outcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(EVALUATION_DATE.minusDays(1), 4, 4, STABLE))),
                List.of()));

    assertThat(notifications.messages)
        .containsExactly(
            """
            Riskiindikaatori muutus
            ⚠️ TUV100 SRRI — avaldatav klass muutus 4 → 5 (kehtib alates 2026-05-05)""");
  }

  @Test
  void anUnchangedStateSendsNoImmediateAlert() {
    disclose(SRRI, TUV100, 5);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    outcome(
                        staleDocumentSrri(),
                        new PublicationSnapshot(
                            EVALUATION_DATE.minusDays(1), 5, 5, CHANGE_CONFIRMED))),
                List.of()));

    assertThat(notifications.messages).isEmpty();
  }

  @Test
  void aFundThatCouldNotBeEvaluatedIsNamedInTheDigest() {
    notifier.notify(
        new RiskIndicatorRun(
            EVALUATION_DATE, List.of(outcome(stableSri(), null)), List.of("TUK75 SRRI: no data")));

    assertThat(notifications.lastMessage())
        .contains("⚠️ Osa fonde jäi hindamata:", "TUK75 SRRI: no data");
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

    notifier.notify(run(indicator));

    assertThat(notifications.lastMessage())
        .contains("⚠️ TUK75 SRRI: 5a aknas 259/260 nädalatootlust");
  }

  @Test
  void theProxyReviewIsRaisedOnceTheFundHasEnoughOwnHistory() {
    proxyReviews.put(TKF100, new ProxyReview(TKF100.getIsin(), 2));
    given(fundValues.findEarliestDateForKey(TKF100.getIsin()))
        .willReturn(Optional.of(LocalDate.of(2026, 2, 2)));
    disclose(SRI, TKF100, 4);

    notifier(FOURTH_BUSINESS_DAY).notify(run(stableSri()));
    var beforeThreshold = notifications.lastMessage();

    notifications.messages.clear();
    var indicator = stableSri();
    var later =
        new PublishedRiskIndicator(
            TKF100,
            SRI,
            LocalDate.of(2028, 2, 3),
            indicator.publishedClass(),
            indicator.rawLatestClass(),
            null,
            indicator.publishedSince(),
            false,
            indicator.rawClassSince(),
            indicator.streakReferencePoints(),
            indicator.rawStreakReferencePoints(),
            indicator.windowReferencePoints(),
            indicator.matchingReferencePoints(),
            indicator.latestObservationCount(),
            indicator.latestVolatility(),
            STABLE);
    notifier(LocalDate.of(2028, 2, 4)).notify(run(later));

    assertThat(beforeThreshold).doesNotContain("proxy vajab ülevaatust");
    assertThat(notifications.lastMessage())
        .contains(
            "⚠️ TKF100 SRI — võrdlusindeksi proxy vajab ülevaatust", "2,0 aastat oma NAV-ajalugu");
  }

  @Test
  void aPendingProxyReviewKeepsTheHeaderOffGreenEvenWhenEveryFundIsStable() {
    proxyReviews.put(TKF100, new ProxyReview(TKF100.getIsin(), 2));
    given(fundValues.findEarliestDateForKey(TKF100.getIsin()))
        .willReturn(Optional.of(LocalDate.of(2019, 1, 1)));
    disclose(SRI, TKF100, 4);

    notifier.notify(run(stableSri()));

    assertThat(notifications.lastMessage())
        .startsWith("⚠️ Riskiindikaatorite kuuülevaade")
        .contains("1 korras, 1 proxy ülevaatust.");
  }

  @Test
  void aFundWithoutAProxyReviewEntryNeverRaisesTheLine() {
    given(fundValues.findEarliestDateForKey(any()))
        .willReturn(Optional.of(LocalDate.of(2000, 1, 1)));
    disclose(SRI, TKF100, 4);

    notifier.notify(run(stableSri()));

    assertThat(notifications.lastMessage()).doesNotContain("proxy vajab ülevaatust");
  }

  @Test
  void aRunThatEvaluatedNothingSaysSoAndFallsBackToTheRunDate() {
    notifier.notify(new RiskIndicatorRun(EVALUATION_DATE, List.of(), List.of()));

    assertThat(notifications.lastMessage())
        .startsWith(
            "✅ Riskiindikaatorite kuuülevaade — seisuga 2026-07-31\nÜhtegi fondi ei hinnatud.");
  }

  @Test
  void theOpenEndedTopClassPrintsItsRangeWithoutAnUpperBound() {
    disclose(SRRI, TUV100, 7);

    notifier.notify(run(topClassSrri()));

    assertThat(notifications.lastMessage())
        .contains("(klassi 7 vahemik 25,00%–∞); lähim piir on 5,00% kaugusel.");
  }

  @Test
  void aStatusChangeThatLeavesThePublishedClassAloneIsStillAlerted() {
    disclose(SRRI, TUK75, 4);

    notifier(LocalDate.of(2026, 8, 5))
        .notify(
            new RiskIndicatorRun(
                EVALUATION_DATE,
                List.of(
                    outcome(
                        pendingSrri(),
                        new PublicationSnapshot(EVALUATION_DATE.minusDays(1), 4, 4, STABLE))),
                List.of()));

    assertThat(notifications.messages)
        .containsExactly(
            """
            Riskiindikaatori muutus
            ⚠️ TUK75 SRRI — staatus STABLE → CHANGE_PENDING (arvutatud klass 5, avaldatav klass 4)""");
  }

  @Test
  void aFailedSendOverAnIncompleteMonthLeavesTheMonthOpenInsteadOfDeletingIt() {
    storeDigestRow(false);
    notifications.failing = true;

    notifier.notify(run(stableSri()));

    assertThat(storedDigestRow())
        .isEqualTo(
            RiskIndicatorDigest.builder()
                .id(1L)
                .digestMonth(DIGEST_MONTH)
                .complete(false)
                .version(2L)
                .build());
    Mockito.verify(digests, Mockito.never()).delete(any());
  }

  @Test
  void aFundWithoutEnoughDataGetsItsOwnBlockAndNoClassColumns() {
    notifier.notify(
        run(
            PublishedRiskIndicator.insufficientData(
                TUK00, SRRI, EVALUATION_DATE, 17, new BigDecimal("0.021000000000"))));

    assertThat(notifications.lastMessage())
        .contains(
            "TUK00   SRRI    —          ?          —              —        —        ⚠️ andmeid napib",
            "⚠️ TUK00 SRRI — andmeid napib",
            "Aknas on 17 vaatlust, klassi ei avaldata. Volatiilsus 2,10%.",
            "👉 Tegevus: kontrolli, kas NAV-seeria on täielik. Kui fondi enda ajalugu ongi nõutud"
                + " perioodist lühem, lisa investment.risk.sources alla võrdlusindeksi segment —"
                + " klassi avaldamata jätmine ei ole lubatud variant.");
  }

  @Test
  void aConfirmedChangeThatTheDocumentAlreadyReflectsIsSpelledOutRatherThanFlaggedRed() {
    disclose(SRRI, TUV100, 5);

    notifier.notify(run(staleDocumentSrri()));

    assertThat(notifications.lastMessage())
        .contains(
            "⚠️ muutus kinnitatud",
            "⚠️ TUV100 SRRI — muutus äsja kinnitatud",
            "Avaldatav klass 5 alates 2026-05-05 (eelmine 4).",
            "👉 Tegevus: kontrolli, kas dokument on juba uuendatud.");
  }

  @Test
  void aClassWithoutAVolatilityStillPrintsItsRange() {
    disclose(SRI, TKF100, 4);

    notifier.notify(run(withVolatility(stableSri(), null)));

    assertThat(notifications.lastMessage()).contains("Klassi 4 vahemik 0,1200–0,2000.");
  }

  @Test
  void aPendingSriChangeCountsTheReferencePointsStillMissingForTheMajority() {
    disclose(SRI, TKF100, 4);

    notifier.notify(run(pendingSri()));

    assertThat(notifications.lastMessage())
        .contains(
            "Klass 5 on hoidnud 30 kauplemispäeva alates 2026-06-15."
                + " PRIIPs enamuseni puudu 13 punkti.");
  }

  @Test
  void thePendingSrriLineCountsTheWindowPointsThatStillBlockTheMigration() {
    disclose(SRRI, TUK75, 4);

    notifier.notify(run(pendingSrri()));

    assertThat(notifications.lastMessage())
        .contains("eeldatav kinnitus 2026-09-25; aknas on veel 8 referentspunkti muus klassis.");
  }

  @Test
  void aPendingSrriChangeWithoutAKnownStartDateOnlyReportsTheStreak() {
    disclose(SRRI, TUK75, 4);

    notifier.notify(run(withRawClassSince(pendingSrri(), null)));

    assertThat(notifications.lastMessage()).contains("Klass 5 on hoidnud 9 nädalat.");
  }

  @Test
  void theProxyReviewStopsOnceTheIndicatorAlreadyReadsTheFundsOwnHistory() {
    proxyReviews.put(TKF100, new ProxyReview("MSCI_ACWI", 2));
    given(fundValues.findEarliestDateForKey(any()))
        .willReturn(Optional.of(LocalDate.of(2000, 1, 1)));
    disclose(SRI, TKF100, 4);

    notifier.notify(run(stableSri()));

    assertThat(notifications.lastMessage()).doesNotContain("proxy vajab ülevaatust");
  }

  @Test
  void theProxyReviewStaysSilentWhileTheFundHasNoOwnHistoryAtAll() {
    proxyReviews.put(TKF100, new ProxyReview(TKF100.getIsin(), 2));
    given(fundValues.findEarliestDateForKey(TKF100.getIsin())).willReturn(Optional.empty());
    disclose(SRI, TKF100, 4);

    notifier.notify(run(stableSri()));

    assertThat(notifications.lastMessage()).doesNotContain("proxy vajab ülevaatust");
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

  private RiskIndicatorRun run(PublishedRiskIndicator... indicators) {
    return new RiskIndicatorRun(
        EVALUATION_DATE,
        List.of(indicators).stream().map(i -> outcome(i, null)).toList(),
        List.of());
  }

  private RiskIndicatorOutcome outcome(
      PublishedRiskIndicator indicator, @Nullable PublicationSnapshot previous) {
    return new RiskIndicatorOutcome(
        indicator, previous, RiskIndicatorPublication.builder().build(), List.of());
  }

  private static class RecordingNotificationService implements OperationsNotificationService {
    private final List<String> messages = new ArrayList<>();
    private boolean failing = false;

    @Override
    public void sendMessage(String message, Channel channel) {
      if (failing) {
        throw new IllegalStateException("slack is down");
      }
      messages.add(message);
    }

    String lastMessage() {
      return messages.getLast();
    }
  }
}
