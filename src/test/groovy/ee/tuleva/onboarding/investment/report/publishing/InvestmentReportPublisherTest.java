package ee.tuleva.onboarding.investment.report.publishing;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.report.publishing.data.InvestmentReportDataService;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportContext;
import ee.tuleva.onboarding.investment.report.publishing.pdf.InvestmentReportPdfGenerator;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.LeftUnchanged;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.LinkUnknown;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.PageUpdateFailed.NotLinked;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.MayBeStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.UploadFailed.NothingStored;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient;
import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressMediaClient.UploadResult;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class InvestmentReportPublisherTest {

  @Mock private InvestmentReportDataService dataService;
  @Mock private InvestmentReportPdfGenerator pdfGenerator;
  @Mock private WordPressMediaClient wordPressClient;
  @Mock private NewerReportCheck newerReportCheck;
  @Mock private EmailService emailService;
  @Mock private InvestmentReportPublishingLock publishingLock;
  private InvestmentReportPublisher publisher;

  private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);
  private static final List<TulevaFund> PENSION_FUNDS = List.of(TUK75, TUK00, TUV100);
  private static final Set<String> NOTHING_TO_REPLACE = Set.of();
  private static final String HAND_NAMED_FILE =
      "https://tuleva.ee/wp-content/uploads/2026/04/aruanne-marts.pdf";
  private static final Instant NOW = Instant.parse("2026-04-10T07:00:00Z");
  private static final String LIVE_TUK00_REPORT =
      "https://tuleva.ee/wp-content/uploads/2026/04/"
          + "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-03.pdf";

  @BeforeEach
  void setUp() {
    publisher = publisherFor(PENSION_FUNDS);
    lenient().when(dataService.validateQuantities(any(), eq(MARCH_2026))).thenReturn(List.of());
    lenient()
        .when(newerReportCheck.reasonsNotToPublish(any(), any(), any()))
        .thenReturn(new FundPagesBeforePublish(List.of(), Map.of()));
    lenient().when(newerReportCheck.reasonsNotToRepoint(any(), any(), any())).thenReturn(List.of());
    lenient()
        .when(publishingLock.whileNoOtherPublishRuns(any(), any()))
        .thenAnswer(
            publish -> publish.<Function<BooleanSupplier, ?>>getArgument(0).apply(() -> true));
  }

  private InvestmentReportPublisher publisherFor(List<TulevaFund> funds) {
    return new InvestmentReportPublisher(
        dataService,
        pdfGenerator,
        new ReportUploads(wordPressClient, Clock.fixed(NOW, ZoneOffset.UTC)),
        wordPressClient,
        newerReportCheck,
        new InvestmentReportMailer(emailService),
        publishingLock,
        new InvestmentReportPublishingProperties(funds));
  }

  private static final Map<String, LocalDate> CONSISTENT_NAV_DATES =
      Map.of(
          "TUK75", LocalDate.of(2026, 3, 31),
          "TUK00", LocalDate.of(2026, 3, 31),
          "TUV100", LocalDate.of(2026, 3, 31));

  private static final UploadResult SAMPLE_UPLOAD =
      new UploadResult(42, "https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");

  @Test
  void publishRunsFullPipeline() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).hasSize(3);
    assertThat(result.emailSent()).isTrue();
    assertThat(result.errors()).isEmpty();

    verify(wordPressClient, times(3)).upload(any(), any());
    verify(wordPressClient, times(3)).updateAcfReportField(any(), eq(42), any());
    verify(emailService).sendSystemEmail(any());
  }

  @Test
  void aFundLeftOutOfTheConfigurationIsNeitherGeneratedNorPublished() {
    givenEveryReportCanBeProduced();

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
    verify(dataService, never()).getReportData(eq(TKF100), any());
    verify(wordPressClient, never())
        .upload(eq(FundReportMapping.TKF100.buildPdfFilename(MARCH_2026)), any());
    verify(wordPressClient, never())
        .updateAcfReportField(eq(FundReportMapping.TKF100.pageSlug()), anyInt(), any());
  }

  @Test
  void tkf100IsPublishedOnceConfiguredButNotEmailed() {
    var allFunds = List.of(TUK75, TUK00, TUV100, TKF100);
    publisher = publisherFor(allFunds);
    given(dataService.findNavDates(allFunds, MARCH_2026))
        .willReturn(
            Map.of(
                "TUK75", LocalDate.of(2026, 3, 31),
                "TUK00", LocalDate.of(2026, 3, 31),
                "TUV100", LocalDate.of(2026, 3, 31),
                "TKF100", LocalDate.of(2026, 3, 31)));
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    verify(emailService)
        .sendSystemEmail(
            argThat(
                message -> {
                  assertThat(message.getAttachments()).hasSize(3);
                  return true;
                }));

    verify(wordPressClient, times(4)).upload(any(), any());
    verify(wordPressClient, times(4)).updateAcfReportField(any(), eq(42), any());
  }

  @Test
  void publishAbortsWithoutTouchingAnyPageWhenAFundReportCannotBeProduced() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(FundReportMapping.TUK75.fund(), MARCH_2026))
        .willReturn(context);
    given(dataService.getReportData(FundReportMapping.TUK00.fund(), MARCH_2026))
        .willThrow(new IllegalStateException("No NAV data"));
    given(dataService.getReportData(FundReportMapping.TUV100.fund(), MARCH_2026))
        .willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    assertThat(result.emailSent()).isFalse();
    verify(wordPressClient, never()).upload(any(), any());
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt(), any());
    verify(emailService, never()).sendSystemEmail(any());
  }

  @Test
  void publishAlertsAndSkipsEmailWhenAPageUpdateFailsAfterAllUploads() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund", new BigDecimal("0.998000"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willReturn(SAMPLE_UPLOAD);
    lenient()
        .doThrow(new RuntimeException("ACF down"))
        .when(wordPressClient)
        .updateAcfReportField(eq(FundReportMapping.TUK00.pageSlug()), anyInt(), any());

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    verify(wordPressClient, times(3)).upload(any(), any());
    assertThat(result.errors())
        .containsExactly(
            "WordPress page update failed, so the uploaded report was not linked; link it or delete"
                + " it from the media library: fund=TUK00,"
                + " file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf,"
                + " cause=RuntimeException");
    assertThat(result.wordPressUrls()).hasSize(2).doesNotContainKey("TUK00");
    assertThat(result.emailSent()).isFalse();
    verify(emailService)
        .sendSystemEmail(
            argThat(
                alert ->
                    alert.getSubject().contains("pooleli")
                        && !alert.getText().contains("check the fund page before deleting")));
  }

  @Test
  void publishRejectsInconsistentNavDates() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUK00", LocalDate.of(2026, 3, 30),
            "TUV100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.emailSent()).isFalse();
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void publishRejectsMidMonthNavDate() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 15),
            "TUK00", LocalDate.of(2026, 3, 15),
            "TUV100", LocalDate.of(2026, 3, 15));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.wordPressUrls()).isEmpty();
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void publishRejectsAllocationOutOfRange() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var badContext = sampleContext("Test Fund", new BigDecimal("0.950000"));
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(badContext);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.errors()).isNotEmpty();
    assertThat(result.errors().getFirst()).contains("allocation");
    verifyNoInteractions(wordPressClient, emailService);
  }

  @Test
  void publishPassesValidationWithGoodData() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);

    var context = sampleContext("Test Fund", new BigDecimal("0.998000"));
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.errors()).isEmpty();
    assertThat(result.wordPressUrls()).hasSize(3);
  }

  @Test
  void publishHandlesWordPressUploadFailure() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};

    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), any())).willThrow(new RuntimeException("WP unavailable"));

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).isEmpty();
    assertThat(result.errors()).hasSize(3);
    assertThat(result.emailSent()).isFalse();
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt(), any());
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
    verify(emailService, times(1)).sendSystemEmail(any());
  }

  @Test
  void anUploadThatMayHaveReachedWordPressNamesTheReportToKeepAndTheUploadTimeNotAFilename() {
    givenTheUploadOfTuk00Fails(
        new MayBeStored(
            "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-03.pdf",
            new ResourceAccessException("read timed out")));

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of(
                    "WordPress upload failed after it may have reached WordPress; keep the report"
                        + " the fund page links, and delete only an attachment uploaded at"
                        + " uploadedAt that no fund page links: fund=TUK00,"
                        + " keep="
                        + LIVE_TUK00_REPORT
                        + ", uploadedAt=2026-04-10T07:00:00Z, cause=ResourceAccessException",
                    TUK75_UPLOAD_LEFT_UNLINKED,
                    TUV100_UPLOAD_LEFT_UNLINKED)));
    verify(wordPressClient, never()).updateAcfReportField(any(), anyInt(), any());
  }

  @Test
  void aSameMonthRepublishWhoseUploadNeverConnectsKeepsTheLinkedReportAndAdvisesNoDeletion() {
    givenTheUploadOfTuk00Fails(
        new NothingStored(
            "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-03.pdf",
            new ResourceAccessException("Connection refused")));

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    var nothingStored =
        "WordPress upload did not reach WordPress or was refused, so nothing was stored and"
            + " nothing needs deleting; the fund page still links the report to keep: fund=TUK00,"
            + " keep="
            + LIVE_TUK00_REPORT
            + ", cause=ResourceAccessException";
    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of(nothingStored, TUK75_UPLOAD_LEFT_UNLINKED, TUV100_UPLOAD_LEFT_UNLINKED)));
    verify(emailService)
        .sendSystemEmail(
            argThat(
                alert ->
                    alert
                        .getText()
                        .equals(
                            String.join(
                                System.lineSeparator(),
                                "Investment report publish for 2026-03 did not finish.",
                                "Fund pages updated: []",
                                "Errors: ["
                                    + nothingStored
                                    + ", "
                                    + TUK75_UPLOAD_LEFT_UNLINKED
                                    + ", "
                                    + TUV100_UPLOAD_LEFT_UNLINKED
                                    + "]",
                                "Manual attention needed: make all fund pages consistent, and"
                                    + " link or delete the uploaded files named above."))));
  }

  private static final String TUK75_UPLOAD_LEFT_UNLINKED =
      "Uploaded report was not linked; link it or delete it from the media library:"
          + " fund=TUK75, file=https://tuleva.ee/wp-content/uploads/2026/04/tuk75.pdf";
  private static final String TUV100_UPLOAD_LEFT_UNLINKED =
      "Uploaded report was not linked; link it or delete it from the media library:"
          + " fund=TUV100, file=https://tuleva.ee/wp-content/uploads/2026/04/tuv100.pdf";

  private void givenTheUploadOfTuk00Fails(RuntimeException failure) {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    given(newerReportCheck.reasonsNotToPublish(any(), eq(MARCH_2026), eq(NOTHING_TO_REPLACE)))
        .willReturn(
            new FundPagesBeforePublish(
                List.of(), Map.of(FundReportMapping.TUK00, LIVE_TUK00_REPORT)));
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(FundReportMapping.TUK75.buildPdfFilename(MARCH_2026), pdfBytes))
        .willReturn(new UploadResult(41, "https://tuleva.ee/wp-content/uploads/2026/04/tuk75.pdf"));
    given(wordPressClient.upload(FundReportMapping.TUK00.buildPdfFilename(MARCH_2026), pdfBytes))
        .willThrow(failure);
    given(wordPressClient.upload(FundReportMapping.TUV100.buildPdfFilename(MARCH_2026), pdfBytes))
        .willReturn(
            new UploadResult(43, "https://tuleva.ee/wp-content/uploads/2026/04/tuv100.pdf"));
  }

  @Test
  void publishRejectsMissingNavDateForFund() {
    var navDates =
        Map.of(
            "TUK75", LocalDate.of(2026, 3, 31),
            "TUV100", LocalDate.of(2026, 3, 31));
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(navDates);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().getFirst()).contains("TUK00");
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void nothingIsPublishedWhenAFundPageLinksANewerReportOrCannotBeRead() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var refusal =
        "Fund page already links a newer report: fund=TUK00, linked=2026-04, publishing=2026-03";
    given(
            newerReportCheck.reasonsNotToPublish(
                List.of(FundReportMapping.TUK75, FundReportMapping.TUK00, FundReportMapping.TUV100),
                MARCH_2026,
                NOTHING_TO_REPLACE))
        .willReturn(new FundPagesBeforePublish(List.of(refusal), Map.of()));

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result)
        .isEqualTo(new InvestmentReportPublishingResult(Map.of(), false, List.of(refusal)));
    verifyNoInteractions(pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void aPageThatCameToLinkANewerReportDuringThePublishIsLeftAlone() {
    givenEveryReportCanBeProduced();
    var refusal =
        "Fund page already links a newer report: fund=TUK00, linked=2026-04, publishing=2026-03";
    given(
            newerReportCheck.reasonsNotToRepoint(
                FundReportMapping.TUK00, MARCH_2026, NOTHING_TO_REPLACE))
        .willReturn(List.of(refusal));

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUV100");
    assertThat(result.errors())
        .containsExactly(
            refusal,
            "Uploaded report was not linked; link it or delete it from the media library:"
                + " fund=TUK00, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    verify(wordPressClient, never())
        .updateAcfReportField(eq(FundReportMapping.TUK00.pageSlug()), anyInt(), any());
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
  }

  @Test
  void aFileThePageLinksThatIsNotTheFundsReportIsReplacedOnlyWhenTheAdminNamesIt() {
    givenEveryReportCanBeProduced();
    var refusal =
        "Fund page links a file that is not this fund's report, so it may be newer: fund=TUK00";
    given(newerReportCheck.reasonsNotToPublish(any(), eq(MARCH_2026), eq(NOTHING_TO_REPLACE)))
        .willReturn(new FundPagesBeforePublish(List.of(refusal), Map.of()));

    var refused = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);
    var replaced = publisher.publish(MARCH_2026, Set.of(HAND_NAMED_FILE));

    assertThat(refused)
        .isEqualTo(new InvestmentReportPublishingResult(Map.of(), false, List.of(refusal)));
    assertThat(replaced.errors()).isEmpty();
    assertThat(replaced.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
    verify(newerReportCheck)
        .reasonsNotToRepoint(FundReportMapping.TUK00, MARCH_2026, Set.of(HAND_NAMED_FILE));
  }

  @Test
  void aSecondPublishWhileOneIsRunningPublishesNothingAndSaysWhenTheLockRunsOut() {
    willAnswer(
            refused ->
                refused
                    .<Function<String, ?>>getArgument(1)
                    .apply(
                        "Another investment report publish holds the lock and extends it while it"
                            + " runs; one that stopped loses it by heldUntil:"
                            + " heldUntil=2026-04-10T07:02:00Z"))
        .given(publishingLock)
        .whileNoOtherPublishRuns(any(), any());

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(),
                false,
                List.of(
                    "Another investment report publish holds the lock and extends it while it"
                        + " runs; one that stopped loses it by heldUntil:"
                        + " heldUntil=2026-04-10T07:02:00Z, month=2026-03")));
    verifyNoInteractions(
        dataService, newerReportCheck, pdfGenerator, wordPressClient, emailService);
  }

  @Test
  void aPublishThatLostItsLockRepointsNoFurtherPageAndAlerts() {
    givenEveryReportCanBeProduced();
    givenTheLockIsHeldFor(1);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75");
    assertThat(result.errors())
        .containsExactly(
            "The publish lost its lock, so this fund page was not repointed: fund=TUK00",
            "Uploaded report was not linked; link it or delete it from the media library:"
                + " fund=TUK00, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf",
            "The publish lost its lock, so this fund page was not repointed: fund=TUV100",
            "Uploaded report was not linked; link it or delete it from the media library:"
                + " fund=TUV100, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    verify(wordPressClient)
        .updateAcfReportField(eq(FundReportMapping.TUK75.pageSlug()), anyInt(), any());
    verify(wordPressClient, times(1)).updateAcfReportField(any(), anyInt(), any());
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
    verify(emailService, times(1)).sendSystemEmail(any());
  }

  @Test
  void aPublishThatLostItsLockBeforeTheEmailAlertsInsteadOfEmailingTheReports() {
    givenEveryReportCanBeProduced();
    givenTheLockIsHeldFor(3);

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUK00", "TUV100");
    assertThat(result.emailSent()).isFalse();
    assertThat(result.errors())
        .containsExactly(
            "The publish lost its lock after repointing the fund pages, so the reports were not"
                + " e-mailed: month=2026-03");
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
    verify(emailService, times(1)).sendSystemEmail(any());
  }

  @Test
  void aPublishThatLosesItsLockWhileReadingAPageDoesNotUpdateItAndAlerts() {
    givenEveryReportCanBeProduced();
    givenTheLockIsHeldFor(3);
    willAnswer(
            update -> {
              if (!update.<BooleanSupplier>getArgument(2).getAsBoolean()) {
                throw new LeftUnchanged(update.getArgument(0), new IllegalStateException());
              }
              return null;
            })
        .given(wordPressClient)
        .updateAcfReportField(any(), anyInt(), any());

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75");
    assertThat(result.errors())
        .containsExactly(
            "The publish lost its lock, so this fund page was not repointed: fund=TUK00",
            "Uploaded report was not linked; link it or delete it from the media library:"
                + " fund=TUK00, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf",
            "The publish lost its lock, so this fund page was not repointed: fund=TUV100",
            "Uploaded report was not linked; link it or delete it from the media library:"
                + " fund=TUV100, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf");
    verify(emailService)
        .sendSystemEmail(argThat(message -> message.getSubject().contains("pooleli")));
    verify(emailService, times(1)).sendSystemEmail(any());
  }

  @Test
  void aPageUpdateWhoseOutcomeCannotBeReadBackIsReportedAsUnknownNotUnlinked() {
    givenEveryReportCanBeProduced();
    lenient()
        .doThrow(
            new LinkUnknown(
                FundReportMapping.TUK00.pageSlug(), new ResourceAccessException("read timed out")))
        .when(wordPressClient)
        .updateAcfReportField(eq(FundReportMapping.TUK00.pageSlug()), anyInt(), any());

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result.wordPressUrls()).containsOnlyKeys("TUK75", "TUV100");
    assertThat(result.errors())
        .containsExactly(
            "WordPress page update did not finish in a way the page shows, so whether it links the"
                + " uploaded report is unknown; check the fund page before deleting anything:"
                + " fund=TUK00, file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf,"
                + " cause=ResourceAccessException");
    verify(emailService)
        .sendSystemEmail(
            argThat(
                alert ->
                    alert.getSubject().contains("pooleli")
                        && alert.getText().contains("check the fund page before deleting")));
  }

  @Test
  void aPageUpdateWordPressAnsweredButThePageDoesNotShowIsReportedNotLinked() {
    givenEveryReportCanBeProduced();
    lenient()
        .doThrow(
            new NotLinked(
                FundReportMapping.TUK00.pageSlug(),
                new HttpClientErrorException(HttpStatus.BAD_REQUEST)))
        .when(wordPressClient)
        .updateAcfReportField(eq(FundReportMapping.TUK00.pageSlug()), anyInt(), any());

    var result = publisher.publish(MARCH_2026, NOTHING_TO_REPLACE);

    assertThat(result)
        .isEqualTo(
            new InvestmentReportPublishingResult(
                Map.of(
                    "TUK75", SAMPLE_UPLOAD.sourceUrl(),
                    "TUV100", SAMPLE_UPLOAD.sourceUrl()),
                false,
                List.of(
                    "WordPress page update failed, so the uploaded report was not linked; link it"
                        + " or delete it from the media library: fund=TUK00,"
                        + " file=https://tuleva.ee/wp-content/uploads/2026/04/test.pdf,"
                        + " cause=HttpClientErrorException")));
  }

  private void givenTheLockIsHeldFor(int checks) {
    var checksLeft = new AtomicInteger(checks);
    willAnswer(
            publish ->
                publish
                    .<Function<BooleanSupplier, ?>>getArgument(0)
                    .apply(() -> checksLeft.getAndDecrement() > 0))
        .given(publishingLock)
        .whileNoOtherPublishRuns(any(), any());
  }

  private void givenEveryReportCanBeProduced() {
    given(dataService.findNavDates(PENSION_FUNDS, MARCH_2026)).willReturn(CONSISTENT_NAV_DATES);
    var context = sampleContext("Test Fund");
    var pdfBytes = new byte[] {0x25, 0x50, 0x44, 0x46};
    given(dataService.getReportData(any(), eq(MARCH_2026))).willReturn(context);
    given(pdfGenerator.generatePdf(context)).willReturn(pdfBytes);
    given(wordPressClient.upload(any(), eq(pdfBytes))).willReturn(SAMPLE_UPLOAD);
    given(emailService.sendSystemEmail(any())).willReturn(true);
  }

  private static InvestmentReportContext sampleContext(String fundTitle) {
    return sampleContext(fundTitle, new BigDecimal("0.998000"));
  }

  private static InvestmentReportContext sampleContext(
      String fundTitle, BigDecimal totalAssetsNavPercent) {
    return new InvestmentReportContext(
        fundTitle,
        "31.03.2026",
        List.of(),
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        null,
        BigDecimal.ZERO,
        null,
        totalAssetsNavPercent,
        new BigDecimal("10000000"));
  }
}
