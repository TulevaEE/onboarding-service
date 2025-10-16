package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.statement.BankAccountType;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
@Profile("!staging")
public class SwedbankStatementFetcher {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;

  // @Scheduled(cron = "0 0 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  @Scheduled(cron = "0 0 12 * * TUE", zone = "Europe/Tallinn")
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

    var requestEntity = swedbankGatewayClient.getIntraDayReportRequestEntity(accountIban, id);
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
        swedbankGatewayClient.getHistoricReportRequestEntity(accountIban, id, fromDate, toDate);
    swedbankGatewayClient.sendStatementRequest(requestEntity, id);
  }
}
