package ee.tuleva.onboarding.banking.payment;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.banking.BankAccounts;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentAccounts {
  private final BankAccounts bankAccounts;

  List<AccountPayments> group(List<OutgoingPayment> payments) {
    return payments.stream()
        .collect(groupingBy(OutgoingPayment::getRemitterIban))
        .entrySet()
        .stream()
        .map(entry -> new AccountPayments(nameOf(entry.getKey()), entry.getKey(), entry.getValue()))
        .sorted(comparing(AccountPayments::accountName))
        .toList();
  }

  private String nameOf(String iban) {
    return bankAccounts.find(iban).map(account -> account.type().name()).orElse(iban);
  }
}
