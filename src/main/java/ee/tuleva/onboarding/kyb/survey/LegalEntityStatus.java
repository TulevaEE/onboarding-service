package ee.tuleva.onboarding.kyb.survey;

import ee.tuleva.onboarding.kyb.CompanyStatus;

enum LegalEntityStatus {
  REGISTERED,
  IN_LIQUIDATION,
  DELETED,
  BANKRUPT;

  static LegalEntityStatus fromCompanyStatus(CompanyStatus companyStatus) {
    return switch (companyStatus) {
      case R -> REGISTERED;
      case L -> IN_LIQUIDATION;
      case N -> DELETED;
      case K -> BANKRUPT;
    };
  }
}
