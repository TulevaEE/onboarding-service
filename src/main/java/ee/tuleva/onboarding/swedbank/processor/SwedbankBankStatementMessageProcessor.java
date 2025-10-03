package ee.tuleva.onboarding.swedbank.processor;

import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;

import ee.tuleva.onboarding.savings.fund.SavingFundPaymentExtractor;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentUpsertionService;
import ee.tuleva.onboarding.swedbank.statement.SavingsFundAccountIdentifier;
import ee.tuleva.onboarding.swedbank.statement.SwedbankBankStatementExtractor;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SwedbankBankStatementMessageProcessor {

  private final SwedbankBankStatementExtractor swedbankBankStatementExtractor;
  private final SavingFundPaymentExtractor paymentExtractor;
  private final SavingFundPaymentUpsertionService paymentService;
  private final SavingsFundAccountIdentifier savingsFundAccountIdentifier;
  private final Clock clock;

  @Transactional
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

    var accountIban = bankStatement.getBankStatementAccount().iban();
    if (!savingsFundAccountIdentifier.isAccountType(accountIban, DEPOSIT_EUR)) {
      log.info(
          "Skipping payment processing for account {} as it is not a DEPOSIT_EUR account",
          accountIban);
      return;
    }

    var payments = paymentExtractor.extractPayments(bankStatement);

    log.info("Successfully extracted {} payments from a bank statement", payments.size());

    payments.forEach(paymentService::upsert);

    log.info("Successfully upserted {} payments", payments.size());
  }
}
