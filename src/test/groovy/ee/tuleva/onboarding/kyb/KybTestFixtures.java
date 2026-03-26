package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.L;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.kyb.LegalForm.AS;
import static ee.tuleva.onboarding.kyb.LegalForm.OÜ;

import java.math.BigDecimal;
import java.util.List;

public final class KybTestFixtures {

  static final PersonalCode JAAN = new PersonalCode("38501010002");
  static final PersonalCode MARI = new PersonalCode("49001010001");
  static final PersonalCode PEETER = new PersonalCode("37801010009");

  static final CompanyDto VALID_COMPANY =
      new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", OÜ);

  static final SelfCertification VALID_CERT = new SelfCertification(true, true, true);

  static KybRelatedPerson person(
      PersonalCode code,
      boolean boardMember,
      boolean shareholder,
      boolean beneficialOwner,
      BigDecimal ownership,
      KybKycStatus kycStatus) {
    return new KybRelatedPerson(
        code, boardMember, shareholder, beneficialOwner, ownership, kycStatus);
  }

  static KybCompanyData rule31Pass() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rule31Fail_notBeneficialOwner() {
    var owner = person(JAAN, true, true, false, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rule32Pass() {
    var person1 = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var person2 = person(MARI, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(person1, person2), VALID_CERT);
  }

  static KybCompanyData rule32Fail_incompleteOwnership() {
    var person1 = person(JAAN, true, true, true, BigDecimal.valueOf(30), COMPLETED);
    var person2 = person(MARI, true, true, true, BigDecimal.valueOf(30), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(person1, person2), VALID_CERT);
  }

  static KybCompanyData rule33Pass() {
    var boardMember = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var otherOwner = person(MARI, false, true, true, BigDecimal.valueOf(50), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(boardMember, otherOwner), VALID_CERT);
  }

  static KybCompanyData rule33Fail_boardMemberNotOwner() {
    var boardMember = person(JAAN, true, false, false, BigDecimal.ZERO, COMPLETED);
    var owner = person(MARI, false, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(boardMember, owner), VALID_CERT);
  }

  static KybCompanyData rule34Fail_companyInLiquidation() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, L, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rules36to40Fail_kycNotCompleted() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var failedKyc = person(MARI, true, true, true, BigDecimal.valueOf(50), REJECTED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, failedKyc), VALID_CERT);
  }

  static KybCompanyData rules36to40Fail_kycUnknown() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var unknownKyc = person(MARI, true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, unknownKyc), VALID_CERT);
  }

  static KybCompanyData rule41Fail_highRiskNace() {
    var company = new CompanyDto(new RegistryCode("12345678"), "Crypto OÜ", "64321", OÜ);
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(company, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rule50Fail_notOÜ() {
    var company = new CompanyDto(new RegistryCode("12345678"), "Test AS", "62011", AS);
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(company, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData selfCertificationFail() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var badCert = new SelfCertification(true, false, true);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), badCert);
  }

  static KybCompanyData threeRelatedPersons() {
    var person1 = person(JAAN, true, true, true, BigDecimal.valueOf(34), COMPLETED);
    var person2 = person(MARI, true, true, true, BigDecimal.valueOf(33), COMPLETED);
    var person3 = person(PEETER, false, true, true, BigDecimal.valueOf(33), COMPLETED);
    return new KybCompanyData(
        VALID_COMPANY, JAAN, R, List.of(person1, person2, person3), VALID_CERT);
  }

  private KybTestFixtures() {}
}
