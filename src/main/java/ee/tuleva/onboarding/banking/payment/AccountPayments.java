package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.amount;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBrief.count;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.List;

record AccountPayments(String accountName, String iban, List<OutgoingPayment> payments) {

  String summary() {
    return "  %s  %s  %s EUR".formatted(accountName, count(payments.size()), amount(total()));
  }

  BigDecimal total() {
    return payments.stream().map(OutgoingPayment::getAmount).reduce(ZERO, BigDecimal::add);
  }
}
