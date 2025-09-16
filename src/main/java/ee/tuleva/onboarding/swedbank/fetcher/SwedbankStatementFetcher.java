package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetchJob.JobStatus.*;

import ee.swedbank.gateway.iso.response.Document;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Profile({"dev"})
@RequiredArgsConstructor
@Slf4j
@Service
public class SwedbankStatementFetcher {

  private final Clock clock;

  private static final String accountIban = "EE062200221055091966"; // TODO test value

  private final SwedbankStatementFetchJobRepository swedbankStatementFetchJobRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;

  @Scheduled(cron = "0 */15 9-17 * * MON-FRI")
  public void sendRequest() {
    log.info("Running Swedbank statement request sender");

    var lastPendingFetchJob =
        swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(
            WAITING_FOR_REPLY);
    var oneHourAgo = clock.instant().minus(1, ChronoUnit.HOURS);

    if (lastPendingFetchJob.isPresent()
        && lastPendingFetchJob.get().getCreatedAt().isAfter(oneHourAgo)) {
      log.info(
          "Last pending job id={} was less than 1 hour ago, skipping...",
          lastPendingFetchJob.get().getId());
      return;
    }

    var fetchJob =
        swedbankStatementFetchJobRepository.save(
            SwedbankStatementFetchJob.builder().jobStatus(SCHEDULED).build());

    try {
      swedbankGatewayClient.sendStatementRequest(
          swedbankGatewayClient.getAccountStatementRequestEntity(accountIban, fetchJob.getId()),
          fetchJob.getId());
      fetchJob.setJobStatus(WAITING_FOR_REPLY);

    } catch (RestClientException e) {
      fetchJob.setJobStatus(FAILED);
      fetchJob.setRawResponse(e.getMessage());
      throw e;
    } finally {
      swedbankStatementFetchJobRepository.save(fetchJob);
    }
  }

  @Scheduled(cron = "0 */5 9-17 * * MON-FRI")
  public void getResponse() {
    log.info("Running Swedbank statement response fetcher");

    var optionalLastInProgressFetchJob =
        swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(
            WAITING_FOR_REPLY);

    if (optionalLastInProgressFetchJob.isEmpty()) {
      log.info("No WAITING_FOR_REPLY Swedbank statement job found...");
      return;
    }

    var lastInProgressFetchJob = optionalLastInProgressFetchJob.get();

    var lastRequestTime =
        Optional.ofNullable(lastInProgressFetchJob.getLastCheckAt())
            .orElse(lastInProgressFetchJob.getCreatedAt());
    var oneMinuteAgo = clock.instant().minus(1, ChronoUnit.MINUTES);

    var oneMinutePassedAfterLastCheck = lastRequestTime.isBefore(oneMinuteAgo);
    if (!oneMinutePassedAfterLastCheck) {
      log.info(
          "Last check for Swedbank statement job id={} was less than 1 minute ago, skipping... ",
          lastInProgressFetchJob.getId());
      return;
    }

    var optionalResponse = swedbankGatewayClient.getResponse();
    lastInProgressFetchJob.setLastCheckAt(clock.instant());
    swedbankStatementFetchJobRepository.save(lastInProgressFetchJob);

    if (optionalResponse.isEmpty()) {
      log.info("Swedbank statement response is not ready yet... ");
      return;
    }

    var response = optionalResponse.get();

    // Even though we hope that swedbank returns the response for the last job, there really is no
    // guarantee as no ID is taken
    var optionalJobForResponseFromSwedbank =
        swedbankStatementFetchJobRepository.findById(response.requestTrackingId());

    if (optionalJobForResponseFromSwedbank.isEmpty()) {
      throw new IllegalStateException(
          "No corresponding Swedbank statement job found for swedbank response id="
              + response.requestTrackingId());
    }

    var jobForResponseFromSwedbank = optionalJobForResponseFromSwedbank.get();

    jobForResponseFromSwedbank.setJobStatus(RESPONSE_RECEIVED);
    jobForResponseFromSwedbank.setTrackingId(response.responseTrackingId());
    jobForResponseFromSwedbank.setRawResponse(response.rawResponse());
    swedbankStatementFetchJobRepository.save(jobForResponseFromSwedbank);

    try {
      processStatementResponse(jobForResponseFromSwedbank);
    } catch (Exception e) {
      log.error("Failed to process Swedbank statement response", e);
    }

    acknowledgeResponse(response, jobForResponseFromSwedbank);
  }

  private void processStatementResponse(SwedbankStatementFetchJob job) {
    Document response = swedbankGatewayClient.getParsedStatementResponse(job.getRawResponse());

    log.info("Swedbank statement response: {}", response);
  }

  private void acknowledgeResponse(
      SwedbankGatewayResponseDto response, SwedbankStatementFetchJob job) {
    swedbankGatewayClient.acknowledgeResponse(response);

    job.setJobStatus(DONE);
    swedbankStatementFetchJobRepository.save(job);
  }
}
