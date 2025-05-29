package ee.tuleva.onboarding.swedbank.statement;

import ee.swedbank.gateway.request.AccountStatement;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jvnet.hk2.annotations.Service;
import org.springframework.core.convert.converter.Converter;

import javax.xml.datatype.XMLGregorianCalendar;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus.SCHEDULED;
import static ee.tuleva.onboarding.swedbank.statement.SwedbankStatementFetchJob.JobStatus.WAITING_FOR_REPLY;

@Service
@RequiredArgsConstructor
@Slf4j
public class SwedbankStatementFetcher {

  private final Clock clock;

  private final String accountIban = "EE_TEST_IBAN";

  private final SwedbankStatementFetchJobRepository swedbankStatementFetchJobRepository;
  private final SwedbankGatewayClient swedbankGatewayClient;


  private final Converter<LocalDate, XMLGregorianCalendar> dateConverter;


  public void sendRequest() {
    log.info("Running Swedbank statement request sender");

    var lastScheduleFetchJob =  swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(SCHEDULED);

    // TODO
    if (lastScheduleFetchJob.getCreatedAt().isAfter(clock.instant().minus(1, ChronoUnit.HOURS))) {
      log.info("Last scheduled job was less than 1 hour ago, skipping...");
      return;
    }

    var savedJob = swedbankStatementFetchJobRepository.save(SwedbankStatementFetchJob.builder().jobStatus(SCHEDULED).build());

    swedbankGatewayClient.sendStatementRequest(getAccountStatementRequestEntity(), savedJob.getId().toString());

    savedJob.setJobStatus(WAITING_FOR_REPLY);
    swedbankStatementFetchJobRepository.save(savedJob);
  }


  public SwedbankStatementFetchJob getResponse() {
    log.info("Running Swedbank statement response fetcher");


    var lastInProgressFetchJob = swedbankStatementFetchJobRepository.findFirstByJobStatusOrderByCreatedAtDesc(WAITING_FOR_REPLY);
    var lastRequestTime = Optional.ofNullable(lastInProgressFetchJob.getLastCheckAt()).orElse(lastInProgressFetchJob.getCreatedAt());

    
    if (lastRequestTime.isAfter(clock.instant().minus(1, ChronoUnit.MINUTES))) {
      log.info("Last check for job id={} was less than 1 minute ago,  ", lastInProgressFetchJob.getId());
    }
  }


  private AccountStatement getAccountStatementRequestEntity() {
    AccountStatement accountStatement = new AccountStatement();
    accountStatement.setStartDate(dateConverter.convert(LocalDate.from(clock.instant())));
    accountStatement.setEndDate(dateConverter.convert(LocalDate.from(clock.instant())));
    accountStatement.setIBAN(accountIban);

    return accountStatement;
  }
}
