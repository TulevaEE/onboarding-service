package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.WITHIN_TOLERANCE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.MATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.ON_TIME;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SebFundConfirmationImportJobTest {

  private static final String FIRST_KEY = "seb-confirmations/DLA0000001-001.pdf";
  private static final String SECOND_KEY = "seb-confirmations/DLA0000002-001.pdf";
  private static final byte[] FIRST_PDF = {1};
  private static final byte[] SECOND_PDF = {2};
  private static final SebFundConfirmationMissingScan NOTHING_MISSING =
      new SebFundConfirmationMissingScan(List.of(), List.of());
  private static final MissingSebFundConfirmation MISSING_TRADE =
      new MissingSebFundConfirmation(
          "DLA0000009", TUK75, "IE00BKPTWY98", BUY, LocalDate.of(2026, 1, 5), BigDecimal.TEN);

  @Mock private SebFundConfirmationS3Source s3Source;
  @Mock private SebFundConfirmationPdfParser parser;
  @Mock private SebFundConfirmationVerificationService verificationService;
  @Mock private SebFundConfirmationMissingFinder missingFinder;
  @Mock private SebFundConfirmationAuditRecorder auditRecorder;
  @Mock private SebFundConfirmationDigest digest;

  private SebFundConfirmationImportJob job;

  @BeforeEach
  void setUp() {
    job =
        new SebFundConfirmationImportJob(
            s3Source, parser, verificationService, missingFinder, auditRecorder, digest);
    given(s3Source.recentKeys()).willReturn(List.of(FIRST_KEY, SECOND_KEY));
    given(s3Source.get(FIRST_KEY)).willReturn(Optional.of(FIRST_PDF));
    given(s3Source.get(SECOND_KEY)).willReturn(Optional.of(SECOND_PDF));
    given(parser.parse(FIRST_PDF)).willReturn(confirmation("DLA0000001"));
    given(parser.parse(SECOND_PDF)).willReturn(confirmation("DLA0000002"));
    given(verificationService.verify(any())).willReturn(clean());
    given(missingFinder.find(any())).willReturn(NOTHING_MISSING);
  }

  @Test
  void run_reportsAnUnreadablePdfAndStillChecksTheOthers() {
    willThrow(new SebFundConfirmationPdfParseException("Glyph not in table"))
        .given(parser)
        .parse(SECOND_PDF);

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001")),
            List.of(),
            List.of(new UnreadableSebFundConfirmation(SECOND_KEY, "Glyph not in table")),
            List.of());
  }

  @Test
  void run_skipsAnObjectThatDisappearedBeforeItCouldBeFetched() {
    given(s3Source.get(SECOND_KEY)).willReturn(Optional.empty());

    job.run();

    then(digest).should().publish(List.of(outcome("DLA0000001")), List.of(), List.of(), List.of());
  }

  @Test
  void run_reportsAConfirmationThatCouldNotBeDownloadedAndStillChecksTheOthers() {
    willThrow(new IllegalStateException("S3 unavailable")).given(s3Source).get(SECOND_KEY);

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001")),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "kinnituse " + SECOND_KEY + " allalaadimine", "S3 unavailable")));
  }

  @Test
  void run_reportsAVerificationThatCouldNotRunAndStillVerifiesTheOthers() {
    willThrow(new IllegalStateException("report store down"))
        .given(verificationService)
        .verify(confirmation("DLA0000001"));

    job.run();

    then(auditRecorder)
        .should()
        .recordVerified(outcome("DLA0000002"), "seb-fund-confirmation-import-job");
    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000002")),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "tehingu DLA0000001 kontroll", "report store down")));
  }

  @Test
  void run_reportsThatTheConfirmationsCouldNotBeListed() {
    willThrow(new IllegalStateException("S3 unavailable")).given(s3Source).recentKeys();

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "kinnituste nimekirja lugemine S3-st", "S3 unavailable")));
  }

  @Test
  void run_checksOnlyTheLatestUploadOfATradeSebConfirmedTwice() {
    String correctedKey = "seb-confirmations/DLA0000001-002.pdf";
    byte[] correctedPdf = {3};
    given(s3Source.recentKeys()).willReturn(List.of(FIRST_KEY, SECOND_KEY, correctedKey));
    given(s3Source.get(correctedKey)).willReturn(Optional.of(correctedPdf));
    given(parser.parse(correctedPdf)).willReturn(confirmation("DLA0000001", "12.5"));

    job.run();

    then(verificationService).should(never()).verify(confirmation("DLA0000001"));
    then(digest)
        .should()
        .publish(
            List.of(
                new SebFundConfirmationOutcome(confirmation("DLA0000001", "12.5"), clean()),
                outcome("DLA0000002")),
            List.of(),
            List.of(),
            List.of());
  }

  @Test
  void run_reportsAResultThatCouldNotBeSavedAndStillAlertsOnIt() {
    willThrow(new IllegalStateException("audit store down"))
        .given(auditRecorder)
        .recordVerified(outcome("DLA0000001"), "seb-fund-confirmation-import-job");

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001"), outcome("DLA0000002")),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "tehingu DLA0000001 tulemuse salvestamine", "audit store down")));
  }

  @Test
  void run_countsEveryUploadedPdfAsAConfirmationWhenLookingForMissingOnes() {
    job.run();

    then(missingFinder).should().find(Set.of("DLA0000001", "DLA0000002"));
  }

  @Test
  void run_stillLooksForMissingConfirmationsWhenNoPdfArrives() {
    given(s3Source.recentKeys()).willReturn(List.of());
    given(missingFinder.find(Set.of()))
        .willReturn(new SebFundConfirmationMissingScan(List.of(MISSING_TRADE), List.of()));

    job.run();

    then(digest).should().publish(List.of(), List.of(MISSING_TRADE), List.of(), List.of());
  }

  @Test
  void run_doesNotReportAMissingConfirmationForATradeVerifiedBefore() {
    given(missingFinder.find(any()))
        .willReturn(new SebFundConfirmationMissingScan(List.of(MISSING_TRADE), List.of()));
    given(auditRecorder.wasVerified("DLA0000009")).willReturn(true);

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001"), outcome("DLA0000002")), List.of(), List.of(), List.of());
  }

  @Test
  void run_saysWhichSebReportsHadRowsItCouldNotRead() {
    given(missingFinder.find(any()))
        .willReturn(
            new SebFundConfirmationMissingScan(
                List.of(), List.of(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8))));

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001"), outcome("DLA0000002")),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "SEB ootel tehingute raportite lugemine",
                    "lugemata ridu raportites 07.01.2026, 08.01.2026")));
  }

  @Test
  void run_reportsThatTheMissingCheckCouldNotRunAndStillPublishesTheVerifiedConfirmations() {
    willThrow(new IllegalStateException("report store down")).given(missingFinder).find(any());

    job.run();

    then(digest)
        .should()
        .publish(
            List.of(outcome("DLA0000001"), outcome("DLA0000002")),
            List.of(),
            List.of(),
            List.of(
                new SebFundConfirmationCheckFailure(
                    "puuduvate kinnituste otsing", "report store down")));
  }

  private static SebFundConfirmation confirmation(String reference) {
    return confirmation(reference, "12.4");
  }

  private static SebFundConfirmation confirmation(String reference, String price) {
    return new SebFundConfirmation(
        TUK75,
        reference,
        Instant.parse("2026-01-05T09:48:17Z"),
        LocalDate.of(2026, 1, 6),
        LocalDate.of(2026, 1, 9),
        "IE00BKPTWY98",
        BUY,
        new BigDecimal("1000.00"),
        new BigDecimal(price),
        new BigDecimal("12400.00"),
        new BigDecimal("0.00"),
        new BigDecimal("12400.00"));
  }

  private static SebFundConfirmationOutcome outcome(String reference) {
    return new SebFundConfirmationOutcome(confirmation(reference), clean());
  }

  private static SebFundConfirmationResult clean() {
    return new SebFundConfirmationResult(
        MATCH, List.of(), WITHIN_TOLERANCE, new BigDecimal("12.4"), BigDecimal.ZERO, ON_TIME, 1);
  }
}
