package ee.tuleva.onboarding.kyb;

public enum KybCheckType {
  COMPANY_STRUCTURE,
  SOLE_MEMBER_OWNERSHIP,
  DUAL_MEMBER_OWNERSHIP,
  SINGLE_BOARD_MEMBER_OWNERSHIP,
  COMPANY_ACTIVE,
  COMPANY_AGE(false),
  RELATED_PERSONS_KYC,
  COMPANY_SANCTION,
  COMPANY_PEP,
  HIGH_RISK_NACE,
  COMPANY_LEGAL_FORM,
  COMPANY_REGISTERED_IN_ESTONIA,
  SELF_CERTIFICATION,
  DATA_CHANGED(false);

  private final boolean onboardingGate;

  KybCheckType() {
    this.onboardingGate = true;
  }

  KybCheckType(boolean onboardingGate) {
    this.onboardingGate = onboardingGate;
  }

  public boolean isOnboardingGate() {
    return onboardingGate;
  }
}
