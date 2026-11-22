package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;

sealed interface KidDigestMessage permits KidCalculationDigest, KidRunFailedDigest {

  String text();

  default Severity severity() {
    return WARNING;
  }
}
