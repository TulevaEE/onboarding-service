package ee.tuleva.onboarding.aml;

import lombok.Getter;

public enum AmlCheckType {
  DOCUMENT,
  RESIDENCY_MANUAL(true),
  RESIDENCY_AUTO,
  PENSION_REGISTRY_NAME,
  SK_NAME,
  POLITICALLY_EXPOSED_PERSON(true),
  OCCUPATION(true),
  CONTACT_DETAILS,
  SANCTION;

  @Getter final boolean manual;

  AmlCheckType() {
    this.manual = false;
  }

  AmlCheckType(boolean manual) {
    this.manual = manual;
  }
}
