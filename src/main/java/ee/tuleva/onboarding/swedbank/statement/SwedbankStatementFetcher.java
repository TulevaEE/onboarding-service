package ee.tuleva.onboarding.swedbank.statement;

import static ee.swedbank.gateway.iso.request.EntryStatus2Code.BOOK;
import static ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus.*;

import ee.swedbank.gateway.iso.request.*;
import ee.swedbank.gateway.request.AccountStatement;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
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

  private final static String accountIban = "EE062200221055091966"; // TODO test value

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
          getAccountStatementRequestEntity(fetchJob.getId()), fetchJob.getId().toString());
      fetchJob.setJobStatus(WAITING_FOR_REPLY);
      swedbankStatementFetchJobRepository.save(fetchJob);

    } catch(RestClientException e) {
      fetchJob.setRawResponse(e.getMessage());
      throw e;
    }
    finally {
      fetchJob.setJobStatus(FAILED);
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

  private AccountReportingRequestV03 getAccountStatementRequestEntity(UUID messageId) {
    AccountReportingRequestV03 accountReportingRequest = new AccountReportingRequestV03();

    GroupHeader59 groupHeader = new GroupHeader59();
    groupHeader.setMsgId(messageId.toString());
    groupHeader.setCreDtTm(timeConverter.convert(clock.instant()));
    accountReportingRequest.setGrpHdr(groupHeader);

    ReportingRequest3 reportingRequest = new ReportingRequest3();

    reportingRequest.setId(messageId.toString());
    reportingRequest.setReqdMsgNmId("camt.053.001.02");


    CashAccount24 cashAccount24 = new CashAccount24();
    AccountIdentification4Choice accountIdentification = new AccountIdentification4Choice();

    accountIdentification.setIBAN(accountIban);

    cashAccount24.setId(accountIdentification);
    reportingRequest.setAcct(cashAccount24);

    reportingRequest.setAcctOwnr(new Party12Choice());

    ReportingPeriod1 period = new ReportingPeriod1();

    DatePeriodDetails1 datePeriodDetails = new DatePeriodDetails1();

    datePeriodDetails.setFrDt(dateConverter.convert(LocalDate.now(clock)));
    datePeriodDetails.setToDt(dateConverter.convert(LocalDate.now(clock)));

    period.setFrToDt(datePeriodDetails);

    TimePeriodDetails1 timePeriodDetails = new TimePeriodDetails1();

    // TODO revisit this, maybe run for last hour to better deal with limits
    timePeriodDetails.setFrTm(timeConverter.convert(LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant()));
    timePeriodDetails.setToTm(timeConverter.convert(LocalDate.now(clock).atStartOfDay(clock.getZone()).with(LocalDate.MAX).toInstant()));

    period.setFrToTm(timePeriodDetails);



    TransactionType1 transactionType = new TransactionType1();
    transactionType.setSts(BOOK); // TODO ?? docs has "ALLL"
    reportingRequest.setReqdTxTp(transactionType);

    accountReportingRequest.getRptgReq().add(reportingRequest);

    return accountReportingRequest;
  }
}
