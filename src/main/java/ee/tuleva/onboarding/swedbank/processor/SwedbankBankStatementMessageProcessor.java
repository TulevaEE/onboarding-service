package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.swedbank.processor.SwedbankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.processor.SwedbankMessageType.INTRA_DAY_REPORT;

import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class SwedbankBankStatementMessageProcessor implements SwedbankMessageProcessor {

  private final SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  private final SavingFundPaymentExtractor paymentExtractor;
  private final Clock clock;

  @Override
  public void processMessage(String rawResponse, SwedbankMessageType messageType) {
    log.info("Processing bank statement message of type {}", messageType);

    var bankStatement =
        switch (messageType) {
          case INTRA_DAY_REPORT ->
              swedbankBankStatementExtractor.extractFromIntraDayReport(rawResponse);
          case HISTORIC_STATEMENT ->
              swedbankBankStatementExtractor.extractFromHistoricStatement(rawResponse);
          case PAYMENT_ORDER_CONFIRMATION ->
              throw new IllegalArgumentException("Message type not supported: " + messageType);
        };
    log.info(
        "Successfully extracted bank statement message with {} entries",
        bankStatement.getEntries().size());

    var receivedAt = clock.instant(); // TODO: get it from the message itself
    var payments = paymentExtractor.extractPayments(bankStatement, receivedAt);

    log.info("Successfully extracted {} payments from a bank statement ", payments.size());
  }

  @Override
  public boolean supports(SwedbankMessageType messageType) {
    return messageType == INTRA_DAY_REPORT || messageType == HISTORIC_STATEMENT;
  }
}
