package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.NOT_PUBLISHED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.WITHIN_TOLERANCE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.LATE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.ON_TIME;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryComparison;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SebFundConfirmationDigestTest {

  @Mock private OperationsNotificationService notificationService;
  @Mock private SebFundConfirmationAuditRecorder auditRecorder;

  private SebFundConfirmationDigest digest;

  @BeforeEach
  void setUp() {
    digest =
        new SebFundConfirmationDigest(
            notificationService,
            auditRecorder,
            new SebFundConfirmationProperties(2, 2, LocalDate.of(2026, 1, 5)));
    given(auditRecorder.unalertedFindings(any()))
        .willAnswer(
            invocation ->
                invocation.<SebFundConfirmationOutcome>getArgument(0).result().findings());
  }

  @Test
  void publish_sendsNothingWhenEveryConfirmationIsClean() {
    digest.publish(List.of(outcome(clean())), List.of(), List.of(), List.of(), List.of());

    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void publish_describesAPriceFarFromTheNavInThreeLines() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                MATCH,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                FAR_FROM_NAV,
                new BigDecimal("12.3000"),
                new BigDecimal("0.8130"),
                ON_TIME,
                1));

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 06.01.2026: hind 12.4 erineb fondi NAV-ist 12.3 +0.81%
            Tehing DLA1234567, SEB sai korralduse 05.01.2026 11:48
            Kontrolli hinda SEB-lt ja fondivalitseja lehelt""",
            INVESTMENT);
    then(auditRecorder).should().recordAlerted(outcome, List.of("FAR_FROM_NAV"));
  }

  @Test
  void publish_postsOnlyTheFindingThatWasNotAlertedBefore() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                MATCH,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                FAR_FROM_NAV,
                new BigDecimal("12.3000"),
                new BigDecimal("0.8130"),
                LATE,
                3));
    given(auditRecorder.unalertedFindings(outcome)).willReturn(List.of("LATE"));

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost: hinnapäev 06.01.2026 on 3 NAV-i päeva pärast korralduse saamist (lubatud 2)
            Tehing DLA1234567, SEB sai korralduse 05.01.2026 11:48
            Küsi SEB-lt, miks korraldus hiljem täideti""",
            INVESTMENT);
    then(auditRecorder).should().recordAlerted(outcome, List.of("LATE"));
  }

  @Test
  void publish_describesEachProblemOfOneConfirmationSeparately() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                MISMATCH,
                List.of("kogus 1000.00 vs 999", "väärtuspäev 2026-01-09 vs 2026-01-10"),
                RegistryComparison.NOT_IN_REGISTRY,
                WITHIN_TOLERANCE,
                new BigDecimal("12.3456"),
                new BigDecimal("0.0006"),
                LATE,
                4));

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 06.01.2026: SEB tehingukinnitus ei klapi ootel tehingute raportiga (kogus 1000.00 vs 999; väärtuspäev 2026-01-09 vs 2026-01-10)
            Tehing DLA1234567, SEB sai korralduse 05.01.2026 11:48
            Võrdle kinnitust ja raportit, vajadusel küsi SEB-lt parandust

            TUK75 IE00BKPTWY98 ost: hinnapäev 06.01.2026 on 4 NAV-i päeva pärast korralduse saamist (lubatud 2)
            Tehing DLA1234567, SEB sai korralduse 05.01.2026 11:48
            Küsi SEB-lt, miks korraldus hiljem täideti""",
            INVESTMENT);
  }

  @Test
  void publish_reportsATradeTheSebReportNeverHeld() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                NOT_FOUND,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                WITHIN_TOLERANCE,
                new BigDecimal("12.3456"),
                new BigDecimal("0.0006"),
                ON_TIME,
                1));

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 06.01.2026: tehingut pole SEB ootel tehingute raportis
            Tehing DLA1234567, väärtuspäev 09.01.2026
            Küsi SEB-lt, kas tehing on registreeritud""",
            INVESTMENT);
  }

  @Test
  void publish_reportsMissingAndUnreadableConfirmations() {
    MissingSebFundConfirmation missing =
        new MissingSebFundConfirmation(
            "DLA7654321",
            TUK00,
            "LU0826455353",
            SELL,
            LocalDate.of(2026, 1, 7),
            new BigDecimal("908"));
    UnreadableSebFundConfirmation unreadable =
        new UnreadableSebFundConfirmation(
            "seb-confirmations/DLA1111111-001.pdf", "Glyph not in table");

    digest.publish(List.of(), List.of(missing), List.of(unreadable), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK00 LU0826455353 müük 07.01.2026: SEB tehingukinnitust pole 2 tööpäeva järel
            Tehing DLA7654321 on SEB ootel tehingute raportis, kogus 908
            Kontrolli funds@ postkasti ja küsi kinnitust SEB-lt

            SEB tehingukinnitust ei õnnestunud lugeda: seb-confirmations/DLA1111111-001.pdf
            Põhjus: Glyph not in table
            Kontrolli PDF-i käsitsi""",
            INVESTMENT);
    then(auditRecorder).should().recordAlerted(missing);
    then(auditRecorder).should().recordAlerted(unreadable);
  }

  @Test
  void publish_saysWhichPartOfTheCheckCouldNotRunAndRecordsItForTheDay() {
    SebFundConfirmationCheckFailure failure =
        new SebFundConfirmationCheckFailure("puuduvate kinnituste otsing", "report store down");

    digest.publish(List.of(), List.of(), List.of(), List.of(), List.of(failure));

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            SEB tehingukinnituste kontroll jäi pooleli: puuduvate kinnituste otsing
            Põhjus: report store down
            Teavita IT-d ja kontrolli seda osa seni käsitsi""",
            INVESTMENT);
    then(auditRecorder).should().recordAlerted(failure);
  }

  @Test
  void publish_doesNotRepeatAPartThatCouldNotRunOnTheSameDay() {
    SebFundConfirmationCheckFailure failure =
        new SebFundConfirmationCheckFailure("puuduvate kinnituste otsing", "report store down");
    given(auditRecorder.alreadyAlertedToday(failure)).willReturn(true);

    digest.publish(List.of(), List.of(), List.of(), List.of(), List.of(failure));

    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void publish_saysThePriceCouldNotBeCheckedWhenTheNavNeverArrived() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                MATCH,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                NOT_PUBLISHED,
                null,
                null,
                ON_TIME,
                1));

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService)
        .should()
        .sendMessage(
            """
            SEB fonditehingute kinnitused

            TUK75 IE00BKPTWY98 ost 06.01.2026: hinnapäeva NAV-i pole hinnaandmetes, hinda ei saanud kontrollida
            Tehing DLA1234567, SEB sai korralduse 05.01.2026 11:48
            Võrdle hinda fondivalitseja lehega ja teavita IT-d, et fondi NAV hinnaandmetesse ei jõua""",
            INVESTMENT);
  }

  @Test
  void publish_doesNotRepeatAnAlreadyAlertedFinding() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                NOT_FOUND,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                WITHIN_TOLERANCE,
                null,
                null,
                ON_TIME,
                1));
    given(auditRecorder.unalertedFindings(outcome)).willReturn(List.of());

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(notificationService).should(never()).sendMessage(any(), any());
  }

  @Test
  void publish_recordsNothingWhenSlackFails() {
    SebFundConfirmationOutcome outcome =
        outcome(
            new SebFundConfirmationResult(
                NOT_FOUND,
                List.of(),
                RegistryComparison.NOT_IN_REGISTRY,
                WITHIN_TOLERANCE,
                null,
                null,
                ON_TIME,
                1));
    willThrow(new IllegalStateException("slack down"))
        .given(notificationService)
        .sendMessage(any(), any());

    digest.publish(List.of(outcome), List.of(), List.of(), List.of(), List.of());

    then(auditRecorder).should(never()).recordAlerted(any(), any());
  }

  private static SebFundConfirmationResult clean() {
    return new SebFundConfirmationResult(
        MATCH,
        List.of(),
        RegistryComparison.NOT_IN_REGISTRY,
        WITHIN_TOLERANCE,
        new BigDecimal("12.3456"),
        new BigDecimal("0.0006"),
        ON_TIME,
        1);
  }

  private static SebFundConfirmationOutcome outcome(SebFundConfirmationResult result) {
    return new SebFundConfirmationOutcome(
        new SebFundConfirmation(
            TUK75,
            "DLA1234567",
            Instant.parse("2026-01-05T09:48:17Z"),
            LocalDate.of(2026, 1, 6),
            LocalDate.of(2026, 1, 9),
            "IE00BKPTWY98",
            BUY,
            new BigDecimal("1000.00"),
            new BigDecimal("12.4"),
            new BigDecimal("12400.00"),
            new BigDecimal("0.00"),
            new BigDecimal("12400.00")),
        result);
  }
}
