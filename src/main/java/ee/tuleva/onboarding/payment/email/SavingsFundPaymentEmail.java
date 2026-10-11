package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_FAIL;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SavingsFundPaymentEmail(EmailType emailType, Map<String, Object> mergeVars) {

  private static final String ACCOUNT_ID = "recipientAccountId";
  private static final String NAME = "recipientName";

  static SavingsFundPaymentEmail cancelled() {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_CANCEL, Map.of());
  }

  static SavingsFundPaymentEmail failed() {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_FAIL, Map.of());
  }

  static SavingsFundPaymentEmail personSuccess() {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON, Map.of());
  }

  static SavingsFundPaymentEmail childSuccess(UUID accountId) {
    return new SavingsFundPaymentEmail(
        SAVINGS_FUND_PAYMENT_SUCCESS_CHILD,
        Map.of("recipientIsChild", true, ACCOUNT_ID, accountId.toString()));
  }

  static SavingsFundPaymentEmail childSuccess(String childName, UUID accountId) {
    return new SavingsFundPaymentEmail(
        SAVINGS_FUND_PAYMENT_SUCCESS_CHILD,
        Map.of(
            "recipientIsChild",
            true,
            ACCOUNT_ID,
            accountId.toString(),
            NAME,
            Names.formatted(childName)));
  }

  // A company that is not on file yet: the receipt is still the company one, its call to
  // action just opens the company account the reader represents rather than a named one.
  static SavingsFundPaymentEmail companySuccess() {
    return new SavingsFundPaymentEmail(
        SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, Map.of("recipientIsCompany", true));
  }

  static SavingsFundPaymentEmail companySuccess(String companyName, UUID accountId) {
    return new SavingsFundPaymentEmail(
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
