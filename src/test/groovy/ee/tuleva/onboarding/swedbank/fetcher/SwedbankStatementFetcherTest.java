package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetchJob.JobStatus.*;
import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.SwedbankAccount.DEPOSIT_EUR;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.swedbank.gateway.iso.request.Document;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetchJob.JobStatus;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import ee.tuleva.onboarding.time.TestClockHolder;
import jakarta.xml.bind.JAXBElement;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class SwedbankStatementFetcherTest {

  private SwedbankGatewayClient swedbankGatewayClient;

  private SwedbankStatementFetchJobRepository fetchJobRepository;

  private SwedbankStatementFetcher fetcher;

  private SwedbankAccountConfiguration configuration;

  private static final String testIban = "EE_TEST_IBAN";

  @BeforeEach
  void setup() {
    swedbankGatewayClient = mock(SwedbankGatewayClient.class);
    fetchJobRepository = mock(SwedbankStatementFetchJobRepository.class);
    configuration = mock(SwedbankAccountConfiguration.class);

    fetcher =
        new SwedbankStatementFetcher(
            TestClockHolder.clock, fetchJobRepository, swedbankGatewayClient, configuration);

    when(configuration.getAccountIban(DEPOSIT_EUR)).thenReturn(Optional.of(testIban));
  }

  @Test
  @DisplayName("request sender sends request")
  void testSendRequest() {
    JAXBElement<Document> mockDocument = mock(JAXBElement.class);

    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");

    var mockScheduledJob = SwedbankStatementFetchJob.builder().jobStatus(SCHEDULED).id(id).build();
    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .iban(testIban)
            .build();

    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(any(), any()))
        .thenReturn(Optional.empty());
    when(fetchJobRepository.save(any()))
        .thenReturn(mockScheduledJob)
        .thenReturn(mockWaitingForReplyJob);

    when(swedbankGatewayClient.getAccountStatementRequestEntity(any(), any()))
        .thenReturn(mockDocument);

    Set<JobStatus> savedStatuses = EnumSet.noneOf(JobStatus.class);

    when(fetchJobRepository.save(any()))
        .thenAnswer(
            invocation -> {
              SwedbankStatementFetchJob job = invocation.getArgument(0);
              savedStatuses.add(job.getJobStatus());
              job.setId(id);
              return job;
            });

    fetcher.sendRequest(DEPOSIT_EUR);

    verify(swedbankGatewayClient, times(1)).sendStatementRequest(eq(mockDocument), eq(id));

    assertTrue(savedStatuses.contains(WAITING_FOR_REPLY));
    assertTrue(savedStatuses.contains(SCHEDULED));
    assertEquals(2, savedStatuses.size());
  }

  @Test
  @DisplayName("request sender sends request when last request was more than 1 hour ago")
  void testSendRequestLastOneMoreThanOneHourAgo() {
    JAXBElement<Document> mockDocument = mock(JAXBElement.class);

    var testIban = "EE_TEST_IBAN";
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");

    var lastScheduledJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(SCHEDULED)
            .createdAt(TestClockHolder.now.minus(65, MINUTES))
            .id(UUID.randomUUID())
            .build();

    var mockScheduledJob = SwedbankStatementFetchJob.builder().jobStatus(SCHEDULED).id(id).build();
    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder().jobStatus(WAITING_FOR_REPLY).id(id).build();

    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            any(), eq(testIban)))
        .thenReturn(Optional.of(lastScheduledJob));
    when(fetchJobRepository.save(any()))
        .thenReturn(mockScheduledJob)
        .thenReturn(mockWaitingForReplyJob);

    when(swedbankGatewayClient.getAccountStatementRequestEntity(anyString(), eq(id)))
        .thenReturn(mockDocument);

    fetcher.sendRequest(DEPOSIT_EUR);

    verify(fetchJobRepository, times(2))
        .save(
            argThat(
                job ->
                    job.getJobStatus().equals(WAITING_FOR_REPLY)
                        || job.getJobStatus().equals(SCHEDULED)));
    verify(swedbankGatewayClient, times(1)).sendStatementRequest(mockDocument, id);
  }

  @Test
  @DisplayName(
      "request sender does not create multiple jobs when last one was less than 1 hour ago")
  void testCreateJobLessThanOneHourAgo() {
    var lastScheduledJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(SCHEDULED)
            .createdAt(TestClockHolder.now)
            .id(UUID.randomUUID())
            .build();

    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(any(), any()))
        .thenReturn(Optional.of(lastScheduledJob));

    fetcher.sendRequest(DEPOSIT_EUR);

    verify(fetchJobRepository, times(0)).save(any());
    verify(swedbankGatewayClient, times(0)).sendStatementRequest(any(), any());
  }

  @Test
  @DisplayName("request sender saves raw response when request fails")
  void testFailedResponse() {
    JAXBElement<Document> mockDocument = mock(JAXBElement.class);

    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");
    var mockErrorResponse = "400 <xml><error>Error</error></xml>";

    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(any(), any()))
        .thenReturn(Optional.empty());

    when(swedbankGatewayClient.getAccountStatementRequestEntity(any(), any()))
        .thenReturn(mockDocument);

    HashMap<JobStatus, String> statusToRawResponseMap = new HashMap<>();

    when(fetchJobRepository.save(any()))
        .thenAnswer(
            invocation -> {
              SwedbankStatementFetchJob job = invocation.getArgument(0);
              statusToRawResponseMap.put(job.getJobStatus(), job.getRawResponse());
              job.setId(id);
              return job;
            });

    var exception = new RestClientException(mockErrorResponse);
    doThrow(exception).when(swedbankGatewayClient).sendStatementRequest(any(), any());

    assertThrows(RestClientException.class, () -> fetcher.sendRequest(DEPOSIT_EUR));

    assertEquals(2, statusToRawResponseMap.size());
    assertTrue(statusToRawResponseMap.containsKey(FAILED));
    assertEquals(mockErrorResponse, statusToRawResponseMap.get(FAILED));
  }

  @Test
  @DisplayName("response getter receives and saves response")
  void getAndSaveResponse() {
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");
    var trackingId = UUID.fromString("11111111-a2fd-4118-a6dc-015de60461a8");

    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .createdAt(TestClockHolder.now.minus(2, MINUTES))
            .build();
    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.of(mockWaitingForReplyJob));

    var mockSwedbankResponse =
        new SwedbankGatewayResponseDto("<xml>TEST</xml>", id, trackingId.toString());
    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.of(mockSwedbankResponse));
    when(fetchJobRepository.findById(eq(id))).thenReturn(Optional.of(mockWaitingForReplyJob));

    Map<JobStatus, SwedbankStatementFetchJob> statusToJobMap = new HashMap<>();

    when(fetchJobRepository.save(any()))
        .thenAnswer(
            invocation -> {
              SwedbankStatementFetchJob job = invocation.getArgument(0);
              statusToJobMap.put(job.getJobStatus(), job);
              job.setId(id);
              return job;
            });

    fetcher.getResponse(DEPOSIT_EUR);

    verify(swedbankGatewayClient, times(1)).acknowledgeResponse(eq(mockSwedbankResponse));

    assertEquals(3, statusToJobMap.size());

    var waitingForReplyJob = statusToJobMap.get(WAITING_FOR_REPLY);
    assertEquals(waitingForReplyJob.getLastCheckAt(), TestClockHolder.now);

    var responseReceivedJob = statusToJobMap.get(RESPONSE_RECEIVED);
    assertEquals(mockSwedbankResponse.rawResponse(), responseReceivedJob.getRawResponse());
    assertEquals(mockSwedbankResponse.responseTrackingId(), responseReceivedJob.getTrackingId());
    assertEquals(mockSwedbankResponse.requestTrackingId(), responseReceivedJob.getId());

    var doneJob = statusToJobMap.get(DONE);
    assertEquals(mockSwedbankResponse.rawResponse(), doneJob.getRawResponse());
    assertEquals(mockSwedbankResponse.responseTrackingId(), doneJob.getTrackingId());
    assertEquals(mockSwedbankResponse.requestTrackingId(), doneJob.getId());
  }

  @Test
  @DisplayName("response getter does nothing when job not available")
  void doNothingJobNotAvailable() {

    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.empty());

    fetcher.getResponse(DEPOSIT_EUR);
    verify(swedbankGatewayClient, times(0)).getResponse();
  }

  @Test
  @DisplayName("response getter does not fetch again if last check was less than 1 minute ago")
  void doNothingWhenLessThanOneMinutePassedFromLastCheck() {
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");

    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .createdAt(TestClockHolder.now.minus(30, SECONDS))
            .build();
    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.of(mockWaitingForReplyJob));

    fetcher.getResponse(DEPOSIT_EUR);
    verify(swedbankGatewayClient, times(0)).getResponse();
  }

  @Test
  @DisplayName("response getter updates last check time when response not ready")
  void responseNotReady() {
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");

    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .createdAt(TestClockHolder.now.minus(5, MINUTES))
            .lastCheckAt(TestClockHolder.now.minus(2, MINUTES))
            .build();
    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.of(mockWaitingForReplyJob));

    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.empty());

    fetcher.getResponse(DEPOSIT_EUR);

    verify(fetchJobRepository, times(1))
        .save(
            argThat(
                job ->
                    job.getJobStatus() == WAITING_FOR_REPLY
                        && job.getLastCheckAt() == TestClockHolder.now));
  }

  @Test
  @DisplayName("response getter throws when can't find corresponding job from response")
  void throwInvalidResponse() {
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");
    var swedbankBrokenId = UUID.fromString("ffffffff-a2fd-4118-a6dc-015de60461a8");
    var trackingId = UUID.fromString("11111111-a2fd-4118-a6dc-015de60461a8");

    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .createdAt(TestClockHolder.now.minus(2, MINUTES))
            .build();
    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.of(mockWaitingForReplyJob));

    var mockSwedbankResponse =
        new SwedbankGatewayResponseDto("<xml>TEST</xml>", swedbankBrokenId, trackingId.toString());
    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.of(mockSwedbankResponse));

    when(fetchJobRepository.findById(eq(swedbankBrokenId))).thenReturn(Optional.empty());
    assertThrows(IllegalStateException.class, () -> fetcher.getResponse(DEPOSIT_EUR));
  }

  @Test
  @DisplayName("response getter saves response even if marshalling response to class fails")
  void getResponseFailedMarshal() {
    var id = UUID.fromString("3e79ad6a-a2fd-4118-a6dc-015de60461a8");
    var trackingId = UUID.fromString("11111111-a2fd-4118-a6dc-015de60461a8");

    var mockWaitingForReplyJob =
        SwedbankStatementFetchJob.builder()
            .jobStatus(WAITING_FOR_REPLY)
            .id(id)
            .createdAt(TestClockHolder.now.minus(2, MINUTES))
            .build();
    when(fetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            eq(WAITING_FOR_REPLY), any()))
        .thenReturn(Optional.of(mockWaitingForReplyJob));

    var mockSwedbankResponse =
        new SwedbankGatewayResponseDto("<xml>TEST</xml>", id, trackingId.toString());
    when(swedbankGatewayClient.getResponse()).thenReturn(Optional.of(mockSwedbankResponse));
    when(fetchJobRepository.findById(eq(id))).thenReturn(Optional.of(mockWaitingForReplyJob));

    Map<JobStatus, SwedbankStatementFetchJob> statusToJobMap = new HashMap<>();

    when(fetchJobRepository.save(any()))
        .thenAnswer(
            invocation -> {
              SwedbankStatementFetchJob job = invocation.getArgument(0);
              statusToJobMap.put(job.getJobStatus(), job);
              job.setId(id);
              return job;
            });

    when(swedbankGatewayClient.getParsedStatementResponse(any()))
        .thenThrow(new IllegalStateException("Broken XML"));

    fetcher.getResponse(DEPOSIT_EUR);

    verify(swedbankGatewayClient, times(1)).acknowledgeResponse(eq(mockSwedbankResponse));

    assertEquals(3, statusToJobMap.size());

    var responseReceivedJob = statusToJobMap.get(RESPONSE_RECEIVED);
    assertEquals(mockSwedbankResponse.rawResponse(), responseReceivedJob.getRawResponse());
    assertEquals(mockSwedbankResponse.responseTrackingId(), responseReceivedJob.getTrackingId());
    assertEquals(mockSwedbankResponse.requestTrackingId(), responseReceivedJob.getId());

    var doneJob = statusToJobMap.get(DONE);
    assertEquals(mockSwedbankResponse.rawResponse(), doneJob.getRawResponse());
    assertEquals(mockSwedbankResponse.responseTrackingId(), doneJob.getTrackingId());
    assertEquals(mockSwedbankResponse.requestTrackingId(), doneJob.getId());
  }
}
