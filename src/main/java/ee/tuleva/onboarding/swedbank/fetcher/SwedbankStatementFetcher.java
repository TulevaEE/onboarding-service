package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetchJob.JobStatus.*;

import ee.swedbank.gateway.iso.response.report.Document;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponseDto;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Profile({"!staging"})
@RequiredArgsConstructor
@Slf4j
@Service
public class SwedbankStatementFetcher {

  private final Clock clock;

  private final SwedbankStatementFetchJobRepository swedbankStatementFetchJobRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;

  // mapped from swedbank-gateway.accounts.___ in application properties
  public enum SwedbankAccount {
    DEPOSIT_EUR("deposit_eur");
    // WITHDRAWAL_EUR("withdrawal_eur");

    @Getter private final String configurationKey;

    SwedbankAccount(String configurationKey) {
      this.configurationKey = configurationKey;
    }
  }

  public Optional<SwedbankStatementFetchJob> getById(UUID id) {
    return swedbankStatementFetchJobRepository.findById(id);
  }

  @Scheduled(cron = "0 0 9-17 * * MON-FRI")
  public void sendRequests() {
    for (SwedbankAccount account : SwedbankAccount.values()) {
      sendRequest(account);
    }
  }

  public void sendRequest(SwedbankAccount account) {
    var accountIban =
        swedbankAccountConfiguration
            .getAccountIban(account)
            .orElseThrow(
                () -> new IllegalStateException("No account iban found for account=" + account));

    log.info(
        "Running Swedbank statement request sender for account={} (iban:{})", account, accountIban);

    var lastPendingFetchJob =
        swedbankStatementFetchJobRepository.findFirstByJobStatusAndIbanEqualsOrderByCreatedAtDesc(
            WAITING_FOR_REPLY, accountIban);
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
            SwedbankStatementFetchJob.builder().jobStatus(SCHEDULED).iban(accountIban).build());

    try {
      var requestEntity =
          swedbankGatewayClient.getAccountStatementRequestEntity(accountIban, fetchJob.getId());
      swedbankGatewayClient.sendStatementRequest(requestEntity, fetchJob.getId());
      fetchJob.setJobStatus(WAITING_FOR_REPLY);
      fetchJob.setRawRequest(swedbankGatewayClient.getRequestXml(requestEntity));

    } catch (RestClientException e) {
      fetchJob.setJobStatus(FAILED);
      fetchJob.setRawResponse(e.getMessage());
      throw e;
    } finally {
      swedbankStatementFetchJobRepository.save(fetchJob);
    }
  }

  @Scheduled(cron = "0 */15 9-17 * * MON-FRI")
  public void getResponses() {
    for (SwedbankAccount account : SwedbankAccount.values()) {
      getResponse(account);
    }
  }

  public void getResponse(SwedbankAccount account) {
    var accountIban =
        swedbankAccountConfiguration
            .getAccountIban(account)
            .orElseThrow(
                () -> new IllegalStateException("No account iban found for account=" + account));

    log.info(
        "Running Swedbank statement response fetcher for account={} (iban={})",
        account,
        accountIban);

    var optionalLastInProgressFetchJob =
        swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(
            WAITING_FOR_REPLY);

    if (optionalLastInProgressFetchJob.isEmpty()) {
      log.info("No WAITING_FOR_REPLY Swedbank statement job found");
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
      log.error(
          "No corresponding Swedbank statement job found for swedbank response id="
              + response.requestTrackingId()
              + ", account="
              + account);
      return;
    }

    var jobForResponseFromSwedbank = optionalJobForResponseFromSwedbank.get();

    jobForResponseFromSwedbank.setJobStatus(RESPONSE_RECEIVED);
    jobForResponseFromSwedbank.setTrackingId(response.responseTrackingId());
    jobForResponseFromSwedbank.setRawResponse(response.rawResponse());
    swedbankStatementFetchJobRepository.save(jobForResponseFromSwedbank);

    try {
      getParsedStatementResponse(jobForResponseFromSwedbank);
    } catch (Exception e) {
      log.error("Failed to process Swedbank statement response for account={}", account, e);
    }

    acknowledgeResponse(response, jobForResponseFromSwedbank);
  }

  public Document getParsedStatementResponse(SwedbankStatementFetchJob job) {
    if (job.getRawResponse() == null) {
      throw new IllegalStateException("Job has no response");
    }
    return swedbankGatewayClient.getParsedIntraDayReportResponse(job.getRawResponse());
  }

  private void acknowledgeResponse(
      SwedbankGatewayResponseDto response, SwedbankStatementFetchJob job) {
    swedbankGatewayClient.acknowledgeResponse(response);

    job.setJobStatus(DONE);
    swedbankStatementFetchJobRepository.save(job);
  }
}
