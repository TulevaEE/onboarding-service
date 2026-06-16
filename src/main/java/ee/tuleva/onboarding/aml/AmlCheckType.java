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
  TKF_RISK_LEVEL,
  TKF_RISK_LEVEL_OVERRIDE,
  INTERNAL_ESCALATION,
  // Specialist-logged off-system AML event (manual EDD / phone call / external document check),
  // created via the Metabase dashboard action "Lisa käsitsi AML-sündmus". No automated producer;
  // rows carry metadata.source = "manual". Not consumed by risk views or the mandate gate.
  MANUAL_EVENT,
  KYC_CHECK,
  KYB_COMPANY_STRUCTURE,
  KYB_SOLE_MEMBER_OWNERSHIP,
  KYB_DUAL_MEMBER_OWNERSHIP,
  KYB_SOLE_BOARD_MEMBER_IS_OWNER,
  KYB_SHAREHOLDER_ELIGIBILITY,
  KYB_COMPANY_ACTIVE,
  KYB_COMPANY_AGE,
  KYB_RELATED_PERSONS_KYC,
  KYB_COMPANY_SANCTION,
  KYB_COMPANY_PEP,
  KYB_HIGH_RISK_NACE,
  KYB_COMPANY_LEGAL_FORM,
  KYB_COMPANY_REGISTERED_IN_ESTONIA,
  KYB_SELF_CERTIFICATION,
  KYB_DATA_CHANGED;

  @Getter final boolean manual;

  AmlCheckType() {
    this.manual = false;
  }

  AmlCheckType(boolean manual) {
    this.manual = manual;
  }
}
