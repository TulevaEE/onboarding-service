package ee.tuleva.onboarding.savings.fund.redemption.email;

import ee.tuleva.onboarding.notification.email.EmailType;
import java.util.Map;

record RedemptionConfirmation(EmailType emailType, Map<String, Object> mergeVars) {

  RedemptionConfirmation {
    mergeVars = Map.copyOf(mergeVars);
  }
}
