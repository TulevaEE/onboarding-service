package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_FAIL;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_PAYMENT_SUCCESS;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record SavingsFundPaymentEmail(EmailType emailType, Map<String, Object> mergeVars) {

  static SavingsFundPaymentEmail cancelled() {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_CANCEL, Map.of());
  }

  static SavingsFundPaymentEmail failed() {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_FAIL, Map.of());
  }

  static SavingsFundPaymentEmail personSuccess() {
    return success("person", null);
  }

  static SavingsFundPaymentEmail childSuccess(@Nullable String childName) {
    return success("child", childName);
  }

  static SavingsFundPaymentEmail companySuccess(@Nullable String companyName) {
    return success("company", companyName);
  }

  private static SavingsFundPaymentEmail success(
      String recipientType, @Nullable String recipientName) {
    return new SavingsFundPaymentEmail(
        SAVINGS_FUND_PAYMENT_SUCCESS,
        recipientName == null
            ? Map.of("recipientType", recipientType)
            : Map.of("recipientType", recipientType, "recipientName", recipientName));
  }
}
