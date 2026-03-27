package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KybCompanyDataMapperTest {

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");
  private static final SelfCertification SELF_CERT = new SelfCertification(true, true, true);

  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final KybCompanyDataMapper mapper = new KybCompanyDataMapper(amlCheckRepository);

  @Test
  void mapsPersonWithMultipleRolesToSingleRelatedPerson() {
    var boardMember =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38501010002",
            LocalDate.of(1985, 1, 1),
            LocalDate.of(2020, 1, 1),
            null,
            null,
            null,
            "EST");
    var shareholder =
        new CompanyRelationship(
            "F",
            "OSAN",
            "Osanik",
            "Jaan",
            "Tamm",
            "38501010002",
            LocalDate.of(1985, 1, 1),
            LocalDate.of(2020, 1, 1),
            null,
            new BigDecimal("100.00"),
            "Osaluse kaudu",
            "EST");

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(boardMember, shareholder), SELF_CERT);

    assertThat(result.company())
        .isEqualTo(new CompanyDto(new RegistryCode("12345678"), "Test OÜ", null, LegalForm.OÜ));
    assertThat(result.personalCode()).isEqualTo(PERSONAL_CODE);
    assertThat(result.status()).isEqualTo(R);
    assertThat(result.selfCertification()).isEqualTo(SELF_CERT);
    assertThat(result.relatedPersons())
        .containsExactly(
            new KybRelatedPerson(
                PERSONAL_CODE, true, true, true, new BigDecimal("100.00"), KybKycStatus.UNKNOWN));
  }

  @Test
  void mapsMultipleDistinctPersons() {
    var person1 =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            null,
            null,
            "EST");
    var person2 =
        new CompanyRelationship(
            "F",
            "OSAN",
            "Osanik",
            "Mari",
            "Kask",
            "49901010003",
            null,
            null,
            null,
            new BigDecimal("50.00"),
            null,
            "EST");

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(person1, person2), SELF_CERT);

    assertThat(result.relatedPersons()).hasSize(2);
    assertThat(result.relatedPersons())
        .containsExactlyInAnyOrder(
            new KybRelatedPerson(
                PERSONAL_CODE, true, false, false, BigDecimal.ZERO, KybKycStatus.UNKNOWN),
            new KybRelatedPerson(
                new PersonalCode("49901010003"),
                false,
                true,
                false,
                new BigDecimal("50.00"),
                KybKycStatus.UNKNOWN));
  }

  @Test
  void mapsBeneficialOwnerByControlMethod() {
    var relationship =
        new CompanyRelationship(
            "F",
            "OSAN",
            "Osanik",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            new BigDecimal("75.00"),
            "Osaluse kaudu",
            "EST");

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(relationship), SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            new KybRelatedPerson(
                PERSONAL_CODE, false, true, true, new BigDecimal("75.00"), KybKycStatus.UNKNOWN));
  }

  @Test
  void excludesFounderOnlyPersons() {
    // Jaan is both board member and founder — should appear
    var jaanBoardMember =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            null,
            null,
            "EST");
    var jaanFounder =
        new CompanyRelationship(
            "F",
            "A",
            "Asutaja",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            null,
            null,
            "EST");
    // Mari is founder only — should be excluded
    var mariFounder =
        new CompanyRelationship(
            "F",
            "A",
            "Asutaja",
            "Mari",
            "Kask",
            "49901010003",
            null,
            null,
            null,
            null,
            null,
            "EST");

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(jaanBoardMember, jaanFounder, mariFounder), SELF_CERT);

    assertThat(result.relatedPersons()).hasSize(1);
    assertThat(result.relatedPersons().getFirst().personalCode()).isEqualTo(PERSONAL_CODE);
    assertThat(result.relatedPersons().getFirst().boardMember()).isTrue();
  }

  @Test
  void mapsCompanyStatus() {
    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), SELF_CERT);

    assertThat(result.status()).isEqualTo(R);
  }

  @Test
  void handlesRelationshipsWithNullPersonalCode() {
    var withCode =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            null,
            null,
            "EST");
    var withoutCode =
        new CompanyRelationship(
            "J",
            "ARP",
            "Aktsiaraamatu pidaja",
            null,
            "Nasdaq CSD SE",
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(withCode, withoutCode), SELF_CERT);

    assertThat(result.relatedPersons()).hasSize(2);
    assertThat(result.relatedPersons())
        .containsExactlyInAnyOrder(
            new KybRelatedPerson(
                PERSONAL_CODE, true, false, false, BigDecimal.ZERO, KybKycStatus.UNKNOWN),
            new KybRelatedPerson(null, false, false, false, BigDecimal.ZERO, KybKycStatus.UNKNOWN));
  }

  @Test
  void mapsKnownLegalFormTüh() {
    var detail = new CompanyDetail("Test TÜH", "12345678", "R", "TÜH", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), SELF_CERT);

    assertThat(result.company().legalForm()).isEqualTo(LegalForm.TÜH);
  }

  @Test
  void mapsUnknownLegalFormToOther() {
    var detail = new CompanyDetail("Test XYZ", "12345678", "R", "XYZ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), SELF_CERT);

    assertThat(result.company().legalForm()).isEqualTo(LegalForm.OTHER);
  }

  @Test
  void picksMaxOwnershipPercentWhenGroupingRoles() {
    var role1 =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            null,
            null,
            "EST");
    var role2 =
        new CompanyRelationship(
            "F",
            "OSAN",
            "Osanik",
            "Jaan",
            "Tamm",
            "38501010002",
            null,
            null,
            null,
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(role1, role2), SELF_CERT);

    assertThat(result.relatedPersons())
        .first()
        .extracting(KybRelatedPerson::ownershipPercent)
        .isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void resolvesCompletedKycStatusFromDatabase() {
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndSuccessAndCreatedTimeAfter(
            eq("38501010002"), eq(AmlCheckType.KYC_CHECK), eq(true), any()))
        .thenReturn(true);

    var relationship = boardMemberRelationship("38501010002");
    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(relationship), SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.COMPLETED);
  }

  @Test
  void resolvesRejectedKycStatusFromDatabase() {
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndSuccessAndCreatedTimeAfter(
            eq("38501010002"), eq(AmlCheckType.KYC_CHECK), eq(true), any()))
        .thenReturn(false);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndSuccessAndCreatedTimeAfter(
            eq("38501010002"), eq(AmlCheckType.KYC_CHECK), eq(false), any()))
        .thenReturn(true);

    var relationship = boardMemberRelationship("38501010002");
    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(relationship), SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.REJECTED);
  }

  @Test
  void resolvesUnknownKycStatusWhenNoCheckExists() {
    var relationship = boardMemberRelationship("38501010002");
    var detail = new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null);

    var result = mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(relationship), SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.UNKNOWN);
  }

  private CompanyRelationship boardMemberRelationship(String personalCode) {
    return new CompanyRelationship(
        "F",
        "JUHL",
        "Juhatuse liige",
        "Jaan",
        "Tamm",
        personalCode,
        null,
        null,
        null,
        null,
        null,
        "EST");
  }
}
