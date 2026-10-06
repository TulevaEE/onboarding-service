package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@DataJpaTest
@Import({
  MissingReportAsOfDateAlertIT.ReportProcessorThatThrows.class,
  ReportImportJob.class,
  SebReportSource.class,
  InvestmentReportService.class,
  FundNavQueryService.class,
  CsvToJsonConverter.class,
  MissingReportAsOfDateAlertListener.class,
  MissingReportAsOfDateAlertIT.TuesdayMorning.class
})
class MissingReportAsOfDateAlertIT {

  private static final String STORED_POSITIONS_KEY = "seb/2026-01-26_positions.csv";
  private static final LocalDate STORED_POSITIONS_DATE = LocalDate.of(2026, 1, 26);
  private static final Instant FIRST_UPLOAD = Instant.parse("2026-01-27T06:10:00Z");
  private static final Instant RESEND = Instant.parse("2026-01-27T08:40:00Z");

  private static final String WITHOUT_AS_OF_LINE =
      """
      Fund Management Company:;Tuleva Fondid AS;;;
      Sent:;2026-01-27;;;
      Report type:;Fund position report;;;
      ;;;;
      ;;;;
      Client name;Account;ISIN;Name;Quantity
      TKF100;tkf100-cash-account;;Cash account in SEB Pank;429062,080
      """;

  private static final String WITH_AS_OF_LINE =
      """
      Fund Management Company:;Tuleva Fondid AS;;;
      Sent:;2026-01-27;;;
      Report type:;Fund position report;;;
      As of:;2026-01-26;;;
      ;;;;
      Client name;Account;ISIN;Name;Quantity
      TKF100;tkf100-cash-account;;Cash account in SEB Pank;429062,080
      """;

  @TestConfiguration
  static class TuesdayMorning {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-01-27T09:00:00Z"), ZoneId.of("Europe/Tallinn"));
    }
  }

  @TestConfiguration
  static class ReportProcessorThatThrows {
    @EventListener(ReportImportCompleted.class)
    void onReportImportCompleted() {
      throw new IllegalStateException("Processing the stored report failed");
    }
  }

  @Autowired ReportImportJob reportImportJob;
  @Autowired NavReportRepository navReportRepository;
  @MockitoBean S3Client s3Client;
  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean PipelineTracker pipelineTracker;
  @MockitoBean PipelineNotifier pipelineNotifier;

  @Test
  void alertsOnce_whenTheImportRunsAgainOverTheSameStoredFileWithNoAsOfDate() {
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);

    reportImportJob.runImport();
    reportImportJob.runImport();

    then(notificationService).should(times(1)).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void alertsAgain_whenSebResendsAFileThatStillHasNoAsOfDate() {
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);
    reportImportJob.runImport();

    sebBucketHolds(WITHOUT_AS_OF_LINE, RESEND);
    reportImportJob.runImport();

    then(notificationService).should(times(2)).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void alertsOnlyForTheBrokenFile_whenSebResendsItWithItsAsOfDate() {
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);
    reportImportJob.runImport();

    sebBucketHolds(WITH_AS_OF_LINE, RESEND);
    reportImportJob.runImport();

    then(notificationService).should(times(1)).sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void alerts_evenWhenAListenerThatProcessesTheStoredReportThrows() {
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);

    reportImportJob.runImport();

    then(notificationService).should().sendMessage(any(), eq(INVESTMENT));
  }

  @Test
  void pingsTheChannel_whileAnyFundsNavForThatDateIsStillToBeCalculated() {
    navIsPublishedFor(List.of(TulevaFund.TUK75, TulevaFund.TUK00));
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);

    reportImportJob.runImport();

    then(notificationService)
        .should()
        .sendMessage(argThat(message -> message.endsWith("<!channel>")), eq(INVESTMENT));
  }

  @Test
  void reportsTheBrokenFileWithoutPingingTheChannel_whenEveryFundsNavForThatDateIsCalculated() {
    navIsPublishedFor(
        Arrays.stream(TulevaFund.values()).filter(TulevaFund::hasNavCalculation).toList());
    sebBucketHolds(WITHOUT_AS_OF_LINE, FIRST_UPLOAD);

    reportImportJob.runImport();

    then(notificationService)
        .should(times(1))
        .sendMessage(argThat(message -> !message.contains("<!channel>")), eq(INVESTMENT));
    then(notificationService).should(times(1)).sendMessage(any(), eq(INVESTMENT));
  }

  private void navIsPublishedFor(List<TulevaFund> funds) {
    funds.forEach(
        fund ->
            navReportRepository.save(
                NavReportRow.builder()
                    .navDate(STORED_POSITIONS_DATE)
                    .fundCode(fund.getCode())
                    .accountType("NAV")
                    .accountName("Net asset value")
                    .calculationId(UUID.randomUUID())
                    .publishedAt(FIRST_UPLOAD)
                    .build()));
  }

  private void sebBucketHolds(String positionsFile, Instant lastModified) {
    given(s3Client.headObject(any(HeadObjectRequest.class)))
        .willReturn(HeadObjectResponse.builder().lastModified(lastModified).build());
    given(s3Client.getObject(any(GetObjectRequest.class)))
        .willAnswer(
            invocation -> {
              GetObjectRequest request = invocation.getArgument(0);
              if (!STORED_POSITIONS_KEY.equals(request.key())) {
                throw NoSuchKeyException.builder().build();
              }
              return new ResponseInputStream<>(
                  GetObjectResponse.builder().build(),
                  AbortableInputStream.create(
                      new ByteArrayInputStream(positionsFile.getBytes(UTF_8))));
            });
  }
}
