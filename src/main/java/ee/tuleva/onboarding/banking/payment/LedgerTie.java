package ee.tuleva.onboarding.banking.payment;

import java.util.List;

record LedgerTie(String label, List<String> mismatches) {

  boolean holds() {
    return mismatches.isEmpty();
  }

  String problem() {
    return "%s: %s".formatted(label, String.join("; ", mismatches));
  }
}
