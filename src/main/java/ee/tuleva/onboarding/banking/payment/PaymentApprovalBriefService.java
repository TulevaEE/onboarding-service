package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.banking.BankAccounts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the brief from the outgoing payment log — from what was sent, not from a re-derivation of
 * what we meant to send, so a generator bug cannot write itself into both the payment and the
 * brief.
 */
@Service
@RequiredArgsConstructor
public class PaymentApprovalBriefService {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final BankAccounts bankAccounts;
  private final Clock clock;

  public PaymentApprovalBrief build(LocalDate date, List<PaymentHold> holds) {
    var dayStart = date.atStartOfDay(TALLINN);
    var submittedToday =
        outgoingPaymentRepository
            .findByAttemptedAtBetween(dayStart.toInstant(), dayStart.plusDays(1).toInstant())
            .stream()
            // Pending approval means sent and not yet known to have moved. A payment already
            // executed was approved earlier and is no longer on the bank's pending screen, so
            // counting it would make the brief disagree with what the signatory is looking at.
            .filter(payment -> payment.getStatus() == SUBMITTED || payment.isPending())
            .toList();

    Map<String, List<OutgoingPayment>> byAccount =
        submittedToday.stream()
            .collect(
                groupingBy(
                    payment -> accountName(payment.getRemitterIban()),
                    LinkedHashMap::new,
                    toList()));

    var accounts =
        byAccount.entrySet().stream()
            .map(entry -> summarise(entry.getKey(), entry.getValue()))
            .sorted(comparing(PaymentApprovalBrief.AccountSummary::accountName))
            .toList();

    // In flight means the call never returned a verdict: the payment may or may not have reached
    // the bank, which is exactly the kind of day that deserves a proper look.
    var inFlight = submittedToday.stream().filter(OutgoingPayment::isPending).count();

    return new PaymentApprovalBrief(
        date,
        accounts,
        holds.size(),
        holds.stream().map(PaymentHold::reason).distinct().toList(),
        !holds.isEmpty() || inFlight > 0);
  }

  private PaymentApprovalBrief.AccountSummary summarise(
      String accountName, List<OutgoingPayment> payments) {
    var flows =
        payments.stream()
            .collect(groupingBy(OutgoingPayment::getPaymentType, LinkedHashMap::new, toList()))
            .entrySet()
            .stream()
            .map(
                entry ->
                    new PaymentApprovalBrief.FlowSummary(
                        label(entry.getKey()), entry.getValue().size(), sum(entry.getValue())))
            .sorted(comparing(PaymentApprovalBrief.FlowSummary::label))
            .toList();

    return new PaymentApprovalBrief.AccountSummary(
        accountName, flows, payments.size(), sum(payments));
  }

  private static BigDecimal sum(List<OutgoingPayment> payments) {
    return payments.stream().map(OutgoingPayment::getAmount).reduce(ZERO, BigDecimal::add);
  }

  private static String label(OutgoingPaymentType type) {
    return switch (type) {
      case SUBSCRIPTION_TRANSFER -> "to fund account";
      case REDEMPTION_TRANSFER -> "to withdrawal account";
      case PAYOUT -> "payouts to clients";
      case RETURN -> "returns to payers";
    };
  }

  /** Our own account, so naming it discloses nothing about a client. */
  private String accountName(String iban) {
    return bankAccounts.find(iban).map(account -> account.type().name()).orElse("UNKNOWN");
  }

  /** A payment Layer 1 or Layer 2 stopped. It has no outgoing payment row, by design. */
  public record PaymentHold(String paymentType, String reason) {}
}
