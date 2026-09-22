package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.seb.SebAccountBalanceReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentApprovalBriefService {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private static final List<Gate> GATES =
      List.of(
          new Gate("PAYMENT_BLOCKED", "file integrity (XSD + parse-back)"),
          new Gate("PAYMENT_MISROUTED", "remitter is one of our accounts"),
          new Gate("PAYOUT_BLOCKED", "payout entitlement"));

  private record Gate(String checkType, String label) {}

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final BankAccounts bankAccounts;
  private final SebAccountBalanceReader balanceReader;

  public PaymentApprovalBrief build(LocalDate date, List<PaymentHold> holds) {
    var awaitingApproval = outgoingPaymentRepository.findAwaitingApproval();
    var attemptedOnCoveredDays = attemptedOnCoveredDays(awaitingApproval, date);

    Map<String, List<OutgoingPayment>> byAccount =
        awaitingApproval.stream()
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

    var inFlight = awaitingApproval.stream().filter(OutgoingPayment::isPending).count();
    var held = holds.stream().filter(PaymentApprovalBriefService::heldByAGate).toList();
    var verdicts = verdicts(attemptedOnCoveredDays, held);

    return new PaymentApprovalBrief(
        date,
        accounts,
        verdicts,
        held.size(),
        held.stream().map(PaymentHold::reason).distinct().toList(),
        !held.isEmpty()
            || inFlight > 0
            || verdicts.stream().anyMatch(verdict -> !verdict.passed())
            || accounts.stream().anyMatch(PaymentApprovalBrief.AccountSummary::goesNegative));
  }

  private List<OutgoingPayment> attemptedOnCoveredDays(
      List<OutgoingPayment> awaitingApproval, LocalDate date) {
    var dayStart = date.atStartOfDay(TALLINN).toInstant();
    return outgoingPaymentRepository
        .findByAttemptedAtBetween(
            earliestCoveredDayStart(awaitingApproval, dayStart),
            date.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .stream()
        .filter(payment -> payment.getStatus() != FAILED)
        .toList();
  }

  private static Instant earliestCoveredDayStart(
      List<OutgoingPayment> awaitingApproval, Instant dayStart) {
    return awaitingApproval.stream()
        .map(payment -> payment.getAttemptedAt().atZone(TALLINN).toLocalDate())
        .map(day -> day.atStartOfDay(TALLINN).toInstant())
        .min(naturalOrder())
        .filter(earliest -> earliest.isBefore(dayStart))
        .orElse(dayStart);
  }

  private static List<PaymentApprovalBrief.Verdict> verdicts(
      List<OutgoingPayment> attempted, List<PaymentHold> holds) {
    var verdicts = new ArrayList<PaymentApprovalBrief.Verdict>();
    crossAccountTie(attempted).ifPresent(verdicts::add);
    GATES.forEach(gate -> verdicts.add(verdictFor(gate, holds)));
    return List.copyOf(verdicts);
  }

  private static Optional<PaymentApprovalBrief.Verdict> crossAccountTie(
      List<OutgoingPayment> attempted) {
    var transferred = totalOf(attempted, REDEMPTION_TRANSFER);
    var paidOut = totalOf(attempted, PAYOUT);
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

  private static boolean heldByAGate(PaymentHold hold) {
    return GATES.stream().anyMatch(gate -> gate.checkType().equals(hold.checkType()));
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

  private String accountName(String iban) {
    return bankAccounts.find(iban).map(account -> account.type().name()).orElse("UNKNOWN");
  }

  public record PaymentHold(String checkType, String reason) {}
}
