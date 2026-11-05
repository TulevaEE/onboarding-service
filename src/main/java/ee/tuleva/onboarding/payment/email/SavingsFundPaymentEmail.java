package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_PAYMENT_FAIL;

import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SavingsFundPaymentEmail(EmailType emailType, Map<String, Object> mergeVars) {

  static SavingsFundPaymentEmail success(SavingsFundAccountHolder holder) {
    return new SavingsFundPaymentEmail(holder.successEmail(), holder.mergeVars());
  }

  static SavingsFundPaymentEmail cancelled(SavingsFundAccountHolder holder) {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_CANCEL, holder.mergeVars());
  }

  static SavingsFundPaymentEmail failed(SavingsFundAccountHolder holder) {
    return new SavingsFundPaymentEmail(SAVINGS_FUND_PAYMENT_FAIL, holder.mergeVars());
  }
}
