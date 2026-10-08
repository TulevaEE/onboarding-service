package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.count;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBriefFormatter.statementTime;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentApprovalReminder {
  private final PaymentAccounts paymentAccounts;
  private final BankingMessageRepository bankingMessageRepository;

  Optional<String> forPaymentsStillUnexecuted(BriefedBatch batch, LocalDate today) {
    var unexecuted = batch.withStatus(SUBMITTED);
    if (unexecuted.isEmpty()) {
      return Optional.empty();
    }

    var accounts =
        paymentAccounts.group(unexecuted).stream()
            .map(account -> account.summary() + "  (" + statementOf(account.iban(), today) + ")")
            .collect(joining("\n"));

    return Optional.of(
        "🟠 TKF100 — %s not yet executed by the bank\n\n%s"
            .formatted(count(unexecuted.size()), accounts));
  }

  private String statementOf(String iban, LocalDate today) {
    return bankingMessageRepository
        .findLatestProcessedStatement(iban)
        .map(BankingMessage::getReceivedAt)
        .map(receivedAt -> "statement " + statementTime(receivedAt, today))
        .orElse("no processed statement");
  }
}
