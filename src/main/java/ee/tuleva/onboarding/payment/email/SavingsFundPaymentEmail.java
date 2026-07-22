package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_FAIL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record SavingsFundPaymentEmail(EmailType emailType, Map<String, Object> mergeVars) {

  static SavingsFundPaymentEmail cancelled() {
    return withoutRecipient(SAVINGS_FUND_PAYMENT_CANCEL);
  }

  static SavingsFundPaymentEmail failed() {
    return withoutRecipient(SAVINGS_FUND_PAYMENT_FAIL);
  }

  static SavingsFundPaymentEmail personSuccess() {
    return withoutRecipient(SAVINGS_FUND_PAYMENT_SUCCESS_PERSON);
  }

  static SavingsFundPaymentEmail childSuccess(@Nullable String childName) {
    return withRecipient(SAVINGS_FUND_PAYMENT_SUCCESS_CHILD, childName);
  }

  static SavingsFundPaymentEmail companySuccess(@Nullable String companyName) {
    return withRecipient(SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, companyName);
  }

  private static SavingsFundPaymentEmail withoutRecipient(EmailType emailType) {
    return new SavingsFundPaymentEmail(emailType, Map.of());
  }

  private static SavingsFundPaymentEmail withRecipient(
      EmailType emailType, @Nullable String recipientName) {
    return recipientName == null
        ? withoutRecipient(emailType)
        : new SavingsFundPaymentEmail(emailType, Map.of("recipientName", recipientName));
  }
}
