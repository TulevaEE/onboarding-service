package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
public class SebStatementFetcher {

  private final SebGatewayClient sebGatewayClient;
  private final BankingMessageRepository bankingMessageRepository;

  @EventListener
  public void onCurrentDayFetchRequested(FetchSebCurrentDayTransactionsRequested event) {
    var account = event.account();
    log.info("Fetching SEB current day transactions: account={}", account);

    String rawXml =
        sebGatewayClient.getCurrentTransactions(account.iban(), account.gatewayClientId());
    persistBankingMessage(rawXml);
  }

  @EventListener
  public void onEodFetchRequested(FetchSebEodTransactionsRequested event) {
    var account = event.account();
    log.info("Fetching SEB end-of-day transactions: account={}", account);

    String rawXml = sebGatewayClient.getEodTransactions(account.iban(), account.gatewayClientId());
    persistBankingMessage(rawXml);
  }

  @EventListener
  public void onHistoricFetchRequested(FetchSebHistoricTransactionsRequested event) {
    var account = event.account();
    log.info(
        "Fetching SEB historic transactions: account={}, dateFrom={}, dateTo={}",
        account,
        event.dateFrom(),
        event.dateTo());

    String rawXml =
        sebGatewayClient.getTransactions(
            account.iban(), account.gatewayClientId(), event.dateFrom(), event.dateTo());
    persistBankingMessage(rawXml);
  }

  private void persistBankingMessage(String rawXml) {
    String messageId = UUID.randomUUID().toString();
    BankingMessage message =
        BankingMessage.builder()
            .bankType(SEB)
            .requestId(messageId)
            .trackingId(messageId)
            .rawResponse(rawXml)
            .timezone(SEB_GATEWAY_TIME_ZONE.getId())
            .build();
    bankingMessageRepository.save(message);
    log.info("Persisted SEB banking message: id={}", message.getId());
  }
}
