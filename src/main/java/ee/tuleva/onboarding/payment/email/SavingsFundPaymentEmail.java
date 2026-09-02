package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_FAIL;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS_PERSON;

import ee.tuleva.onboarding.auth.principal.Names;
import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

  static SavingsFundPaymentEmail childSuccess(@Nullable String childName, UUID accountId) {
    return withRecipient(
        SAVINGS_FUND_PAYMENT_SUCCESS_CHILD, "recipientIsChild", childName, accountId);
  }

  static SavingsFundPaymentEmail companySuccess(
      @Nullable String companyName, @Nullable UUID accountId) {
    return withRecipient(
        SAVINGS_FUND_PAYMENT_SUCCESS_COMPANY, "recipientIsCompany", companyName, accountId);
  }

  private static SavingsFundPaymentEmail withoutRecipient(EmailType emailType) {
    return new SavingsFundPaymentEmail(emailType, Map.of());
  }

  private static SavingsFundPaymentEmail withRecipient(
      EmailType emailType,
      String recipientRoleVariable,
      @Nullable String recipientName,
      @Nullable UUID accountId) {
    var mergeVars = new HashMap<String, Object>();
    mergeVars.put(recipientRoleVariable, true);
    if (accountId != null) {
      mergeVars.put("recipientAccountId", accountId.toString());
    }
    if (recipientName != null) {
      mergeVars.put("recipientName", Names.formatted(recipientName));
    }
    return new SavingsFundPaymentEmail(emailType, Map.copyOf(mergeVars));
  }
}
