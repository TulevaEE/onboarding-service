package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus.*;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
@Slf4j
@Service
public class SwedbankStatementFetcher {

  private final Clock clock;

  private static final String accountIban = "EE062200221055091966"; // TODO test value

  private final SwedbankStatementFetchJobRepository swedbankStatementFetchJobRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;

  private final Converter<LocalDate, XMLGregorianCalendar> dateConverter;
  private final Converter<Instant, XMLGregorianCalendar> timeConverter;

  public void sendRequest() {
    log.info("Running Swedbank statement request sender");

    var lastScheduledFetchJob =
        swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(SCHEDULED);
    var oneHourAgo = clock.instant().minus(1, ChronoUnit.HOURS);

    if (lastScheduledFetchJob.isPresent()
        && !lastScheduledFetchJob.get().getCreatedAt().isBefore(oneHourAgo)) {
      log.info(
          "Last scheduled job id={} was less than 1 hour ago, skipping...",
          lastScheduledFetchJob.get().getId());
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
      swedbankStatementFetchJobRepository.save(fetchJob);

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
        Optional.of(lastInProgressFetchJob.getLastCheckAt())
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

    if (optionalResponse.isEmpty()) {
      log.info(
          "Swedbank statement response for job id={} is not ready yet... ",
          lastInProgressFetchJob.getId());
      swedbankStatementFetchJobRepository.save(lastInProgressFetchJob);
      return;
    }

    var response = optionalResponse.get();

    lastInProgressFetchJob.setJobStatus(RESPONSE_RECEIVED);
    lastInProgressFetchJob.setTrackingId(response.responseTrackingId());
    lastInProgressFetchJob.setRawResponse(response.rawResponse());
    swedbankStatementFetchJobRepository.save(lastInProgressFetchJob);

    processStatementResponse(response);
    acknowledgeResponse(response, lastInProgressFetchJob);
  }

  private void processStatementResponse(SwedbankGatewayResponse response) {
    log.info("Swedbank statement response: {}", response.response()); // TODO to DB
  }

  private void acknowledgeResponse(
      SwedbankGatewayResponse response, SwedbankStatementFetchJob job) {
    swedbankGatewayClient.acknowledgeResponse(response);

    job.setJobStatus(DONE);
    swedbankStatementFetchJobRepository.save(job);
  }
}
