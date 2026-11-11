package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SavingsFundAccountHolder(EmailType successEmail, Map<String, Object> mergeVars) {

  private static final String ACCOUNT_ID = "recipientAccountId";
  private static final String NAME = "recipientName";

  static SavingsFundAccountHolder person() {
    return new SavingsFundAccountHolder(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, Map.of());
  }

  static SavingsFundAccountHolder child(UUID accountId) {
    return new SavingsFundAccountHolder(
        SAVINGS_FUND_PAYMENT_SUCCESS_CHILD,
        Map.of("recipientIsChild", true, ACCOUNT_ID, accountId.toString()));
  }

  static SavingsFundAccountHolder child(String childName, UUID accountId) {
    return new SavingsFundAccountHolder(
        SAVINGS_FUND_PAYMENT_SUCCESS_CHILD,
        Map.of(
            "recipientIsChild",
            true,
            ACCOUNT_ID,
            accountId.toString(),
            NAME,
            Names.formatted(childName)));
  }

  static SavingsFundAccountHolder companyNotOnFileYet() {
    return new SavingsFundAccountHolder(
        SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, Map.of("recipientIsCompany", true));
  }

  static SavingsFundAccountHolder company(String companyName, UUID accountId) {
    return new SavingsFundAccountHolder(
        SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY,
        Map.of(
            "recipientIsCompany",
            true,
            ACCOUNT_ID,
            accountId.toString(),
            NAME,
            Names.formatted(companyName)));
  }
}
