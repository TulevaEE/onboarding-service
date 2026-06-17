package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.L;
import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybKycStatus.*;
import static ee.tuleva.onboarding.kyb.LegalForm.AS;
import static ee.tuleva.onboarding.kyb.LegalForm.OÜ;
import static java.math.BigDecimal.ZERO;

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

  public static KybRelatedPerson.KybRelatedPersonBuilder kybPerson() {
    return KybRelatedPerson.builder().naturalPerson(true).ownershipPercent(ZERO).kycStatus(UNKNOWN);
  }

  public static KybRelatedPerson.KybRelatedPersonBuilder kybPerson(String personalCode) {
    return kybPerson().personalCode(new PersonalCode(personalCode));
  }

  // A board member who is also a shareholder and beneficial owner (the typical owner shape).
  public static KybRelatedPerson.KybRelatedPersonBuilder boardMemberOwner(
      String personalCode, double ownershipPercent) {
    return boardMemberOwner(new PersonalCode(personalCode), ownershipPercent);
  }

  public static KybRelatedPerson.KybRelatedPersonBuilder boardMemberOwner(
      PersonalCode personalCode, double ownershipPercent) {
    return kybPerson()
        .personalCode(personalCode)
        .boardMember(true)
        .shareholder(true)
        .beneficialOwner(true)
        .ownershipPercent(BigDecimal.valueOf(ownershipPercent))
        .kycStatus(COMPLETED);
  }

  // A shareholder and beneficial owner who is not a board member.
  public static KybRelatedPerson.KybRelatedPersonBuilder shareholderOwner(
      String personalCode, double ownershipPercent) {
    return shareholderOwner(new PersonalCode(personalCode), ownershipPercent);
  }

  public static KybRelatedPerson.KybRelatedPersonBuilder shareholderOwner(
      PersonalCode personalCode, double ownershipPercent) {
    return kybPerson()
        .personalCode(personalCode)
        .shareholder(true)
        .beneficialOwner(true)
        .ownershipPercent(BigDecimal.valueOf(ownershipPercent))
        .kycStatus(COMPLETED);
  }

  // A board member who is neither a shareholder nor a beneficial owner.
  public static KybRelatedPerson.KybRelatedPersonBuilder boardMemberOnly(String personalCode) {
    return boardMemberOnly(new PersonalCode(personalCode));
  }

  public static KybRelatedPerson.KybRelatedPersonBuilder boardMemberOnly(
      PersonalCode personalCode) {
    return kybPerson().personalCode(personalCode).boardMember(true).kycStatus(COMPLETED);
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
        "OSAN",
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
        "OSAN",
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

  static CompanyRelationship nasdaqCsdShareholder(
      String personalCode, String firstName, String lastName, BigDecimal ownership) {
    return new CompanyRelationship(
        "F",
        "O", // osanik (shares held in Nasdaq CSD as an "Omanikukonto")
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

  static CompanyRelationship nasdaqBeneficialOwner(
      String personalCode, String firstName, String lastName) {
    return new CompanyRelationship(
        "F",
        "W", // tegelik kasusaaja (beneficial owner)
        "Tegelik kasusaaja",
        firstName,
        lastName,
        personalCode,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        null,
        "Otsene osalus",
        "EST");
  }

  static CompanyRelationship shareRegistrar() {
    return new CompanyRelationship(
        "J", // juriidiline isik (legal entity)
        "ORP", // osade registripidaja (Nasdaq CSD share registrar — not an owner)
        "Osade registripidaja",
        null,
        "Nasdaq CSD SE",
        null,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        null,
        null,
        null);
  }

  static CompanyRelationship legalEntityShareholder(
      String registryCode, String name, BigDecimal ownership) {
    return new CompanyRelationship(
        "J", // juriidiline isik (legal entity) — carries a registry code in the personal-code field
        "OSAN",
        "Osanik",
        null,
        name,
        registryCode,
        null,
        LocalDate.of(2020, 6, 1),
        null,
        ownership,
        null,
        "EST");
  }

  static List<CompanyRelationship> nasdaqCsdSoleOwnerRelationships(String personalCode) {
    return List.of(
        boardMember(personalCode, "Jaan", "Tamm"),
        nasdaqCsdShareholder(personalCode, "Jaan", "Tamm", new BigDecimal("100.00")),
        nasdaqBeneficialOwner(personalCode, "Jaan", "Tamm"),
        shareRegistrar());
  }

  static KybCheck check(KybCheckType type, boolean success) {
    return new KybCheck(type, success, Map.of());
  }

  static KybCompanyData companyData(
      CompanyDto company,
      PersonalCode personalCode,
      CompanyStatus status,
      List<KybRelatedPerson> relatedPersons,
      SelfCertification selfCertification) {
    return new KybCompanyData(
        company,
        personalCode,
        status,
        relatedPersons,
        selfCertification,
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null);
  }

  // A standard active OÜ with the given related persons (defaults for the fields screeners ignore).
  public static KybCompanyData companyWith(KybRelatedPerson... relatedPersons) {
    return companyWith(List.of(relatedPersons));
  }

  public static KybCompanyData companyWith(List<KybRelatedPerson> relatedPersons) {
    return companyData(VALID_COMPANY, JAAN, R, relatedPersons, VALID_CERT);
  }

  // A standard active OÜ with a specific self-certification.
  public static KybCompanyData companyWith(
      SelfCertification selfCertification, KybRelatedPerson... relatedPersons) {
    return companyData(VALID_COMPANY, JAAN, R, List.of(relatedPersons), selfCertification);
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
    var owner = boardMemberOwner(JAAN, 100.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static KybCompanyData rule31Fail_notBeneficialOwner() {
    var owner =
        kybPerson()
            .personalCode(JAAN)
            .boardMember(true)
            .shareholder(true)
            .ownershipPercent(BigDecimal.valueOf(100))
            .kycStatus(COMPLETED)
            .build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(owner), VALID_CERT);
  }

  static List<KybCheck> rule31PassExpectedChecks() {
    return List.of(
        check(COMPANY_STRUCTURE, true),
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
        check(COMPANY_STRUCTURE, true),
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
    var person1 = boardMemberOwner(JAAN, 50.0).build();
    var person2 = boardMemberOwner(MARI, 50.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(person1, person2), VALID_CERT);
  }

  static KybCompanyData rule32Fail_incompleteOwnership() {
    var person1 = boardMemberOwner(JAAN, 30.0).build();
    var person2 = boardMemberOwner(MARI, 30.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(person1, person2), VALID_CERT);
  }

  static List<KybCheck> rule32PassExpectedChecks() {
    return List.of(
        check(COMPANY_STRUCTURE, true),
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
    var boardMember = boardMemberOwner(JAAN, 50.0).build();
    var otherOwner = shareholderOwner(MARI, 50.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(boardMember, otherOwner), VALID_CERT);
  }

  static KybCompanyData rule33Pass_boardMemberIsDirector() {
    var boardMember = boardMemberOnly(JAAN).build();
    var owner = shareholderOwner(MARI, 100.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(boardMember, owner), VALID_CERT);
  }

  static KybCompanyData rule33Fail_incompleteOwnership() {
    var boardMember = boardMemberOwner(JAAN, 50.0).build();
    var owner = shareholderOwner(MARI, 30.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(boardMember, owner), VALID_CERT);
  }

  static List<KybCheck> rule33PassExpectedChecks() {
    return List.of(
        check(COMPANY_STRUCTURE, true),
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
    var owner = boardMemberOwner(JAAN, 100.0).build();
    return companyData(VALID_COMPANY, JAAN, L, List.of(owner), VALID_CERT);
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
    var passedKyc = boardMemberOwner(JAAN, 50.0).build();
    var notCitizen = boardMemberOwner(MARI, 50.0).kycStatus(REJECTED).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, notCitizen), VALID_CERT);
  }

  static KybCompanyData rule37Fail_relatedPersonHighRiskCountry() {
    var passedKyc = boardMemberOwner(JAAN, 50.0).build();
    var highRisk = boardMemberOwner(MARI, 50.0).kycStatus(REJECTED).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, highRisk), VALID_CERT);
  }

  static KybCompanyData rule39Fail_relatedPersonSanctioned() {
    var passedKyc = boardMemberOwner(JAAN, 50.0).build();
    var sanctioned = boardMemberOwner(MARI, 50.0).kycStatus(REJECTED).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, sanctioned), VALID_CERT);
  }

  static KybCompanyData rule40Fail_relatedPersonNotCitizenButResident() {
    var passedKyc = boardMemberOwner(JAAN, 50.0).build();
    var nonCitizenResident = boardMemberOwner(MARI, 50.0).kycStatus(UNKNOWN).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(passedKyc, nonCitizenResident), VALID_CERT);
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
    var owner = boardMemberOwner(JAAN, 100.0).build();
    return companyData(company, JAAN, R, List.of(owner), VALID_CERT);
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
    var owner = boardMemberOwner(JAAN, 100.0).build();
    return companyData(company, JAAN, R, List.of(owner), VALID_CERT);
  }

  // =====================================================================
  // Self certification
  // =====================================================================

  static KybCompanyData selfCertificationPass() {
    return rule31Pass();
  }

  static KybCompanyData selfCertificationFail() {
    var owner = boardMemberOwner(JAAN, 100.0).build();
    var badCert = new SelfCertification(true, false, true);
    return companyData(VALID_COMPANY, JAAN, R, List.of(owner), badCert);
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
    var person1 = boardMemberOwner(JAAN, 34.0).build();
    var person2 = boardMemberOwner(MARI, 33.0).build();
    var person3 = shareholderOwner(PEETER, 33.0).build();
    return companyData(VALID_COMPANY, JAAN, R, List.of(person1, person2, person3), VALID_CERT);
  }

  static List<KybCheck> threePersonsExpectedChecks() {
    return List.of(
        check(COMPANY_STRUCTURE, false),
        check(COMPANY_ACTIVE, true),
        check(RELATED_PERSONS_KYC, true),
        check(COMPANY_SANCTION, true),
        check(COMPANY_PEP, true),
        check(HIGH_RISK_NACE, true),
        check(COMPANY_LEGAL_FORM, true),
        check(SELF_CERTIFICATION, true),
        check(DATA_CHANGED, true));
  }

  private KybTestFixtures() {}
}
