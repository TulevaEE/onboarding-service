package ee.tuleva.onboarding.party;

public enum ParentChildLinkStatus {
  // Authorizes the parent to represent the child (role switch, payments, notifications).
  ACTIVE,
  // Captured for the OTHER guardian(s) at account creation; grants NO access until the co-parent
  // completes their own onboarding/KYC, which flips the link to ACTIVE.
  PENDING_KYC
}
