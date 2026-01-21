package ee.tuleva.onboarding.swedbank.fetcher;

import static ee.tuleva.onboarding.swedbank.Swedbank.SWEDBANK_GATEWAY_TIME_ZONE;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.statement.StatementRequestMessageGenerator;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@RequiredArgsConstructor
@Slf4j
public class SwedbankStatementFetcher {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;
  private final StatementRequestMessageGenerator statementRequestMessageGenerator;

  // @Scheduled(cron = "0 0 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  // @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SwedbankStatementFetcher_sendRequests",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void sendRequests() {
    for (BankAccountType account : BankAccountType.values()) {
      try {
        sendRequest(account);
      } catch (Exception e) {
        log.error("Swedbank statement request sender failed for account={}", account, e);
      }
    }
  }

  public void sendRequest(BankAccountType account) {
    var accountIban = swedbankAccountConfiguration.getAccountIban(account);

    var id = UUID.randomUUID();
    log.info(
        "Running Swedbank intra day report request sender: account={}, iban={}, id={}",
        account,
        accountIban,
        id);

    var requestEntity =
        statementRequestMessageGenerator.generateIntraDayReportRequest(
            accountIban, id, SWEDBANK_GATEWAY_TIME_ZONE);
    swedbankGatewayClient.sendStatementRequest(requestEntity, id);
  }

  public void sendHistoricRequest(BankAccountType account, LocalDate fromDate, LocalDate toDate) {
    var accountIban = swedbankAccountConfiguration.getAccountIban(account);

    var id = UUID.randomUUID();
    log.info(
        "Running Swedbank historic statement request sender: account={}, iban={}, id={}, from={}, to={}",
        account,
        accountIban,
        id,
        fromDate,
        toDate);

    var requestEntity =
        statementRequestMessageGenerator.generateHistoricReportRequest(
            accountIban, id, fromDate, toDate, SWEDBANK_GATEWAY_TIME_ZONE);
    swedbankGatewayClient.sendStatementRequest(requestEntity, id);
  }
}
