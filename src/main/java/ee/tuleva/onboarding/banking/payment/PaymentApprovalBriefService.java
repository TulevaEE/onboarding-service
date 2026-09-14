package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.seb.SebAccountBalanceReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  /**
   * The gates a payment had to pass to be on this list, keyed by the check that holds one back.
   * Named so the reader knows which identity work they no longer have to do by hand.
   */
  private static final List<Gate> GATES =
      List.of(
          new Gate("PAYMENT_BLOCKED", "file integrity (XSD + parse-back)"),
          new Gate("PAYMENT_MISROUTED", "remitter is one of our accounts"),
          new Gate("PAYOUT_BLOCKED", "payout entitlement"));

  private record Gate(String checkType, String label) {}

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final BankAccounts bankAccounts;
  private final SebAccountBalanceReader balanceReader;
  private final Clock clock;

  public PaymentApprovalBrief build(LocalDate date, List<PaymentHold> holds) {
    var dayStart = date.atStartOfDay(TALLINN);
    var attemptedToday =
        outgoingPaymentRepository
            .findByAttemptedAtBetween(dayStart.toInstant(), dayStart.plusDays(1).toInstant())
            .stream()
            .filter(payment -> payment.getStatus() != FAILED)
            .toList();
    var submittedToday =
        attemptedToday.stream()
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
    var verdicts = verdicts(attemptedToday, holds);

    return new PaymentApprovalBrief(
        date,
        accounts,
        verdicts,
        holds.size(),
        holds.stream().map(PaymentHold::reason).distinct().toList(),
        !holds.isEmpty()
            || inFlight > 0
            || verdicts.stream().anyMatch(verdict -> !verdict.passed())
            || accounts.stream().anyMatch(PaymentApprovalBrief.AccountSummary::goesNegative));
  }

  private static List<PaymentApprovalBrief.Verdict> verdicts(
      List<OutgoingPayment> attemptedToday, List<PaymentHold> holds) {
    var verdicts = new ArrayList<PaymentApprovalBrief.Verdict>();
    crossAccountTie(attemptedToday).ifPresent(verdicts::add);
    GATES.forEach(gate -> verdicts.add(verdictFor(gate, holds)));
    return List.copyOf(verdicts);
  }

  /**
   * The transfer exists only to fund the day's payouts, so the two must be equal — the one figure a
   * signatory can verify without leaving the message.
   *
   * <p>Computed over the whole day rather than over what is still pending: an approved transfer
   * leaves the bank's pending screen while its payouts are still on it, and reporting an imbalance
   * every time one account is approved before the other would train the reader to ignore it.
   */
  private static Optional<PaymentApprovalBrief.Verdict> crossAccountTie(
      List<OutgoingPayment> attemptedToday) {
    var transferred = totalOf(attemptedToday, REDEMPTION_TRANSFER);
    var paidOut = totalOf(attemptedToday, PAYOUT);
    if (transferred.signum() == 0 && paidOut.signum() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        new PaymentApprovalBrief.Verdict(
            "payouts == transfer to withdrawal account",
            transferred.compareTo(paidOut) == 0,
            "%s = %s"
                .formatted(
                    PaymentApprovalBrief.amount(paidOut),
                    PaymentApprovalBrief.amount(transferred))));
  }

  private static PaymentApprovalBrief.Verdict verdictFor(Gate gate, List<PaymentHold> holds) {
    var held = holds.stream().filter(hold -> hold.checkType().equals(gate.checkType())).count();
    return new PaymentApprovalBrief.Verdict(
        gate.label(), held == 0, held == 0 ? null : held + " held");
  }

  private static BigDecimal totalOf(List<OutgoingPayment> payments, OutgoingPaymentType type) {
    return sum(payments.stream().filter(payment -> payment.getPaymentType() == type).toList());
  }

  private PaymentApprovalBrief.AccountSummary summarise(
      String accountName, List<OutgoingPayment> payments) {
    var projected =
        payments.stream()
            .map(OutgoingPayment::getRemitterIban)
            .findFirst()
            .flatMap(bankAccounts::find)
            .flatMap(balanceReader::available)
            .map(balance -> balance.subtract(sum(payments)))
            .orElse(null);
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
        accountName, flows, payments.size(), sum(payments), projected);
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
  public record PaymentHold(String checkType, String reason) {}
}
