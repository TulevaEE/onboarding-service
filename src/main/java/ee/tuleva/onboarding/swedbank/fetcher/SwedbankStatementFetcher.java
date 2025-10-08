package ee.tuleva.onboarding.swedbank.fetcher;

import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
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

  // mapped from swedbank-gateway.accounts.___ in application properties
  public enum SwedbankAccount {
    DEPOSIT_EUR("deposit_eur"),
    WITHDRAWAL_EUR("withdrawal_eur"),
    INVESTMENT_EUR("investment_eur");

    @Getter private final String configurationKey;

    SwedbankAccount(String configurationKey) {
      this.configurationKey = configurationKey;
    }
  }

  @Scheduled(cron = "0 0 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  public void sendRequests() {
    for (SwedbankAccount account : SwedbankAccount.values()) {
      try {
        sendRequest(account);
      } catch (Exception e) {
        log.error("Swedbank statement request sender failed for account={}", account, e);
      }
    }
  }

  public void sendRequest(SwedbankAccount account) {
    var accountIban =
        swedbankAccountConfiguration
            .getAccountIban(account)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No account iban found for account=%s".formatted(account)));

    var id = UUID.randomUUID();
    log.info(
        "Running Swedbank intra day report request sender: account={}, iban={}, id={}",
        account,
        accountIban,
        id);

    var requestEntity = swedbankGatewayClient.getIntraDayReportRequestEntity(accountIban, id);
    swedbankGatewayClient.sendStatementRequest(requestEntity, id);
  }

  public void sendHistoricRequest(SwedbankAccount account, LocalDate fromDate, LocalDate toDate) {
    var accountIban =
        swedbankAccountConfiguration
            .getAccountIban(account)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No account iban found for account=%s".formatted(account)));

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
