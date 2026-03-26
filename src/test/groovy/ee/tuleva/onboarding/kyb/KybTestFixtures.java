package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.L;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.kyb.LegalForm.AS;
import static ee.tuleva.onboarding.kyb.LegalForm.OÜ;

import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class KybTestFixtures {

  // --- Personal codes (valid Estonian checksums) ---

  static final PersonalCode JAAN = new PersonalCode("38501010002");
  static final PersonalCode MARI = new PersonalCode("49001010001");
  static final PersonalCode PEETER = new PersonalCode("37801010009");

  // --- Shared builders ---

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

  static CompanyRelationship boardMember(String personalCode, String firstName, String lastName) {
    return new CompanyRelationship(
        "F",
        "JUHL",
        "Juhatuse liige",
        firstName,
        lastName,
        personalCode,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        null,
        null,
        "EST");
  }

  static CompanyRelationship shareholder(
      String personalCode, String firstName, String lastName, BigDecimal ownership) {
    return new CompanyRelationship(
        "F",
        "S",
        "Osanik",
        firstName,
        lastName,
        personalCode,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        ownership,
        null,
        "EST");
  }

  static CompanyRelationship beneficialOwner(
      String personalCode, String firstName, String lastName, BigDecimal ownership) {
    return new CompanyRelationship(
        "F",
        "S",
        "Osanik",
        firstName,
        lastName,
        personalCode,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        ownership,
        "Osaluse kaudu",
        "EST");
  }

  static KybCheck check(KybCheckType type, boolean success) {
    return new KybCheck(type, success, Map.of());
  }

  // =====================================================================
  // Rule 31: Single person OÜ — sole board member is 100% owner
  // =====================================================================

  static final CompanyDetail VALID_COMPANY_DETAIL =
      new CompanyDetail(
          "Test OÜ",
          "12345678",
          "R",
          "OÜ",
          LocalDate.of(2020, 1, 15),
          null,
          "Programmeerimine",
          "62011");

  static List<CompanyRelationship> rule31PassRelationships() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(JAAN.value(), "Jaan", "Tamm", new BigDecimal("100.00")));
  }

  static List<CompanyRelationship> rule31FailRelationships_notBeneficialOwner() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        shareholder(JAAN.value(), "Jaan", "Tamm", new BigDecimal("100.00")));
  }

  static KybCompanyData rule31Pass() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rule31Fail_notBeneficialOwner() {
    var owner = person(JAAN, true, true, false, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static List<KybCheck> rule31PassExpectedChecks() {
    return List.of(
        check(SOLE_MEMBER_OWNERSHIP, true),
        check(COMPANY_ACTIVE, true),
        check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true),
        check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true),
        check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true),
        check(DATA_CHANGED, true));
  }

  static List<KybCheck> rule31FailExpectedChecks() {
    return List.of(
        check(SOLE_MEMBER_OWNERSHIP, false),
        check(COMPANY_ACTIVE, true),
        check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true),
        check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true),
        check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true),
        check(DATA_CHANGED, true));
  }

  // =====================================================================
  // Rule 32: Two person OÜ — two board members are 100% owners
  // =====================================================================

  static List<CompanyRelationship> rule32PassRelationships() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(JAAN.value(), "Jaan", "Tamm", new BigDecimal("50.00")),
        boardMember(MARI.value(), "Mari", "Kask"),
        beneficialOwner(MARI.value(), "Mari", "Kask", new BigDecimal("50.00")));
  }

  static List<CompanyRelationship> rule32FailRelationships_incompleteOwnership() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(JAAN.value(), "Jaan", "Tamm", new BigDecimal("30.00")),
        boardMember(MARI.value(), "Mari", "Kask"),
        beneficialOwner(MARI.value(), "Mari", "Kask", new BigDecimal("30.00")));
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

  static List<KybCheck> rule32PassExpectedChecks() {
    return List.of(
        check(DUAL_MEMBER_OWNERSHIP, true),
        check(COMPANY_ACTIVE, true),
        check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true),
        check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true),
        check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true),
        check(DATA_CHANGED, true));
  }

  // =====================================================================
  // Rule 33: Two person OÜ — sole board member is one of two owners
  // =====================================================================

  static List<CompanyRelationship> rule33PassRelationships() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(JAAN.value(), "Jaan", "Tamm", new BigDecimal("50.00")),
        beneficialOwner(MARI.value(), "Mari", "Kask", new BigDecimal("50.00")));
  }

  static List<CompanyRelationship> rule33FailRelationships_boardMemberNotOwner() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(MARI.value(), "Mari", "Kask", new BigDecimal("100.00")));
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

  static List<KybCheck> rule33PassExpectedChecks() {
    return List.of(
        check(SOLE_BOARD_MEMBER_IS_OWNER, true),
        check(COMPANY_ACTIVE, true),
        check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true),
        check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true),
        check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true),
        check(DATA_CHANGED, true));
  }

  // =====================================================================
  // Rule 34: Company is active in Äriregister
  // =====================================================================

  static CompanyDetail companyDetailInLiquidation() {
    return new CompanyDetail(
        "Test OÜ",
        "12345678",
        "L",
        "OÜ",
        LocalDate.of(2020, 1, 15),
        null,
        "Programmeerimine",
        "62011");
  }

  static KybCompanyData rule34Pass() {
    return rule31Pass();
  }

  static KybCompanyData rule34Fail_companyInLiquidation() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(VALID_COMPANY, JAAN, L, List.of(owner), VALID_CERT);
  }

  // =====================================================================
  // Rule 35: Board member or owner changed (data change detection)
  // =====================================================================

  // Uses rule31Pass() for first run, rule34Fail_companyInLiquidation() for second run

  // =====================================================================
  // Rules 36-40: Related persons KYC
  // 36: Not Estonian citizen or resident → KYC REJECTED
  // 37: High-risk country citizen → KYC REJECTED
  // 38: Estonian citizen lives in high-risk country → KYC REJECTED
  // 39: Related person sanctioned → KYC REJECTED
  // 40: Not Estonian citizen but is resident → KYC UNKNOWN
  // =====================================================================

  static KybCompanyData rule36Fail_relatedPersonNotCitizen() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var notCitizen = person(MARI, true, true, true, BigDecimal.valueOf(50), REJECTED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, notCitizen), VALID_CERT);
  }

  static KybCompanyData rule37Fail_relatedPersonHighRiskCountry() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var highRisk = person(MARI, true, true, true, BigDecimal.valueOf(50), REJECTED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, highRisk), VALID_CERT);
  }

  static KybCompanyData rule39Fail_relatedPersonSanctioned() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var sanctioned = person(MARI, true, true, true, BigDecimal.valueOf(50), REJECTED);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, sanctioned), VALID_CERT);
  }

  static KybCompanyData rule40Fail_relatedPersonNotCitizenButResident() {
    var passedKyc = person(JAAN, true, true, true, BigDecimal.valueOf(50), COMPLETED);
    var nonCitizenResident = person(MARI, true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    return new KybCompanyData(
        VALID_COMPANY, JAAN, R, List.of(passedKyc, nonCitizenResident), VALID_CERT);
  }

  static KybCompanyData rules36to40Pass_allKycCompleted() {
    return rule32Pass();
  }

  // =====================================================================
  // Rule 41: High-risk NACE code
  // =====================================================================

  static CompanyDetail companyDetailHighRiskNace() {
    return new CompanyDetail(
        "Crypto OÜ",
        "12345678",
        "R",
        "OÜ",
        LocalDate.of(2020, 1, 15),
        null,
        "Krüptovarade teenused",
        "64321");
  }

  static KybCompanyData rule41Pass() {
    return rule31Pass();
  }

  static KybCompanyData rule41Fail_highRiskNace() {
    var company = new CompanyDto(new RegistryCode("12345678"), "Crypto OÜ", "64321", OÜ);
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(company, JAAN, R, List.of(owner), VALID_CERT);
  }

  // =====================================================================
  // Rule 43: Company sanctioned (via OpenSanctions)
  // =====================================================================

  // Pass: emptyMatchResponse (default in test setUp)
  // Fail: matchResponseWithTopic("sanction", "ru") — configured in test

  static KybCompanyData rule43Pass() {
    return rule31Pass();
  }

  // =====================================================================
  // Rules 44-45: Company PEP
  // 44: EU PEP (medium risk, passes)
  // 45: Non-EU PEP (fails)
  // =====================================================================

  // Pass: emptyMatchResponse (default in test setUp)
  // EU PEP pass: matchResponseWithTopic("role.pep", "ee")
  // Non-EU PEP fail: matchResponseWithTopic("role.pep", "ru")

  static KybCompanyData rule44Pass() {
    return rule31Pass();
  }

  static KybCompanyData rule45Pass() {
    return rule31Pass();
  }

  // =====================================================================
  // Rule 50: Legal form must be OÜ
  // =====================================================================

  static CompanyDetail companyDetailAS() {
    return new CompanyDetail(
        "Test AS",
        "12345678",
        "R",
        "AS",
        LocalDate.of(2020, 1, 15),
        null,
        "Programmeerimine",
        "62011");
  }

  static KybCompanyData rule50Pass() {
    return rule31Pass();
  }

  static KybCompanyData rule50Fail_notOÜ() {
    var company = new CompanyDto(new RegistryCode("12345678"), "Test AS", "62011", AS);
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(company, JAAN, R, List.of(owner), VALID_CERT);
  }

  // =====================================================================
  // Self certification
  // =====================================================================

  static KybCompanyData selfCertificationPass() {
    return rule31Pass();
  }

  static KybCompanyData selfCertificationFail() {
    var owner = person(JAAN, true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var badCert = new SelfCertification(true, false, true);
    return new KybCompanyData(VALID_COMPANY, JAAN, R, List.of(owner), badCert);
  }

  // =====================================================================
  // Special case: >2 related persons (no ownership rule applies)
  // =====================================================================

  static List<CompanyRelationship> threeRelatedPersonsRelationships() {
    return List.of(
        boardMember(JAAN.value(), "Jaan", "Tamm"),
        beneficialOwner(JAAN.value(), "Jaan", "Tamm", new BigDecimal("34.00")),
        boardMember(MARI.value(), "Mari", "Kask"),
        beneficialOwner(MARI.value(), "Mari", "Kask", new BigDecimal("33.00")),
        beneficialOwner(PEETER.value(), "Peeter", "Mets", new BigDecimal("33.00")));
  }

  static KybCompanyData threeRelatedPersons() {
    var person1 = person(JAAN, true, true, true, BigDecimal.valueOf(34), COMPLETED);
    var person2 = person(MARI, true, true, true, BigDecimal.valueOf(33), COMPLETED);
    var person3 = person(PEETER, false, true, true, BigDecimal.valueOf(33), COMPLETED);
    return new KybCompanyData(
        VALID_COMPANY, JAAN, R, List.of(person1, person2, person3), VALID_CERT);
  }

  static List<KybCheck> threePersonsExpectedChecks() {
    return List.of(
        check(COMPANY_ACTIVE, true), check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true), check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true), check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true), check(DATA_CHANGED, true));
  }

  private KybTestFixtures() {}
}
