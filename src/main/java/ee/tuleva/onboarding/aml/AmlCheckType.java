package ee.tuleva.onboarding.aml;

import lombok.Getter;

public enum AmlCheckType {
  DOCUMENT,
  RESIDENCY_MANUAL(true),
  RESIDENCY_AUTO,
  PENSION_REGISTRY_NAME,
  SK_NAME,
  // TODO: Rename to POLITICALLY_EXPOSED_PERSON_MANUAL and run a db migration and update db views
  POLITICALLY_EXPOSED_PERSON(true),
  POLITICALLY_EXPOSED_PERSON_AUTO,
  OCCUPATION(true),
  CONTACT_DETAILS,
  SANCTION,
  SANCTION_OVERRIDE,
  POLITICALLY_EXPOSED_PERSON_OVERRIDE,
  RISK_LEVEL,
  RISK_LEVEL_OVERRIDE,
  RISK_LEVEL_OVERRIDE_CONFIRMATION,
  INTERNAL_ESCALATION,
  KYC_CHECK;

  @Getter final boolean manual;

  AmlCheckType() {
    this.manual = false;
  }

  AmlCheckType(boolean manual) {
    this.manual = manual;
  }
}
