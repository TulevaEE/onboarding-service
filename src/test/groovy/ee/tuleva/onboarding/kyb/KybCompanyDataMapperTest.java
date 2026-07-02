package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.ariregister.AddressDetails;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyAddress;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KybCompanyDataMapperTest {

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");
  private static final SelfCertification SELF_CERT = new SelfCertification(true, true, true);
  private static final BeneficialOwners NO_BENEFICIAL_OWNERS = BeneficialOwners.none();

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

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(boardMember, shareholder),
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", PERSONAL_CODE.value(), "O")), 0),
            SELF_CERT);

    assertThat(result.company())
        .isEqualTo(new CompanyDto(new RegistryCode("12345678"), "Test OÜ", null, LegalForm.OÜ));
    assertThat(result.personalCode()).isEqualTo(PERSONAL_CODE);
    assertThat(result.status()).isEqualTo(R);
    assertThat(result.selfCertification()).isEqualTo(SELF_CERT);
    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson()
                .personalCode(PERSONAL_CODE)
                .boardMember(true)
                .shareholder(true)
                .beneficialOwner(true)
                .ownershipPercent(new BigDecimal("100.00"))
                .build());
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

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(person1, person2), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons()).hasSize(2);
    assertThat(result.relatedPersons())
        .containsExactlyInAnyOrder(
            kybPerson().personalCode(PERSONAL_CODE).boardMember(true).build(),
            kybPerson("49901010003")
                .shareholder(true)
                .ownershipPercent(new BigDecimal("50.00"))
                .build());
  }

  @Test
  void controlMethodOnRelationshipDoesNotMakeBeneficialOwner() {
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

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson()
                .personalCode(PERSONAL_CODE)
                .shareholder(true)
                .ownershipPercent(new BigDecimal("75.00"))
                .build());
  }

  @Test
  void beneficialOwnerFlagComesFromBeneficialOwnersRegistry() {
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
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(relationship),
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", "38501010002", "O")), 0),
            SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson()
                .personalCode(PERSONAL_CODE)
                .shareholder(true)
                .beneficialOwner(true)
                .ownershipPercent(new BigDecimal("100.00"))
                .build());
  }

  @Test
  void synthesizesRelatedPersonForBeneficialOwnerWithoutRelationships() {
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
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(relationship),
            new BeneficialOwners(
                List.of(new BeneficialOwner("Mari", "Maasikas", "49001010001", "K")), 0),
            SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson()
                .personalCode(PERSONAL_CODE)
                .shareholder(true)
                .ownershipPercent(new BigDecimal("100.00"))
                .build(),
            kybPerson("49001010001").beneficialOwner(true).build());
  }

  @Test
  void synthesizesUnidentifiedPersonPerHiddenBeneficialOwner() {
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(), new BeneficialOwners(List.of(), 2), SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson().beneficialOwner(true).build(), kybPerson().beneficialOwner(true).build());
  }

  @Test
  void synthesizesUnidentifiedPersonForForeignBeneficialOwnerWithoutCode() {
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(),
            new BeneficialOwners(List.of(new BeneficialOwner("Sven", "Svensson", null, "K")), 0),
            SELF_CERT);

    assertThat(result.relatedPersons()).containsExactly(kybPerson().beneficialOwner(true).build());
  }

  @Test
  void mapsCompanyAddressCountryCodeAndFullAddress() {
    var address =
        new CompanyAddress(
            "Tartu maakond, Tartu linn, Paju 2",
            new AddressDetails("Paju 2", "Tartu linn", "50104", "EE"));
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, address, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.countryCode()).isEqualTo("EE");
    assertThat(result.fullAddress()).isEqualTo("Tartu maakond, Tartu linn, Paju 2");
  }

  @Test
  void mapsNullAddressToNullCountryCodeAndFullAddress() {
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.countryCode()).isNull();
    assertThat(result.fullAddress()).isNull();
  }

  @Test
  void mapsCompanyStatus() {
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.status()).isEqualTo(R);
  }

  @Test
  void mapsFoundingDate() {
    var detail =
        new CompanyDetail(
            "Test OÜ",
            "12345678",
            "R",
            "OÜ",
            LocalDate.of(2020, 1, 15),
            null,
            null,
            null,
            List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.foundingDate()).isEqualTo(LocalDate.of(2020, 1, 15));
  }

  @Test
  void mapsNullFoundingDateWhenAriregisterHasNone() {
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.foundingDate()).isNull();
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
    var foreignBoardMember =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "John",
            "Smith",
            null,
            null,
            null,
            null,
            null,
            null,
            "GBR");

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(withCode, foreignBoardMember),
            NO_BENEFICIAL_OWNERS,
            SELF_CERT);

    assertThat(result.relatedPersons()).hasSize(2);
    assertThat(result.relatedPersons())
        .containsExactlyInAnyOrder(
            kybPerson().personalCode(PERSONAL_CODE).boardMember(true).build(),
            kybPerson().personalCode(null).boardMember(true).build());
  }

  @Test
  void mapsKnownLegalFormTüh() {
    var detail =
        new CompanyDetail("Test TÜH", "12345678", "R", "TÜH", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.company().legalForm()).isEqualTo(LegalForm.TÜH);
  }

  @Test
  void mapsUnknownLegalFormToOther() {
    var detail =
        new CompanyDetail("Test XYZ", "12345678", "R", "XYZ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

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

    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(role1, role2), NO_BENEFICIAL_OWNERS, SELF_CERT);

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
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

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
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.REJECTED);
  }

  @Test
  void resolvesUnknownKycStatusWhenNoCheckExists() {
    var relationship = boardMemberRelationship("38501010002");
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.UNKNOWN);
  }

  @Test
  void mapsNasdaqCsdShareholderRoleToShareholder() {
    var shareholder = nasdaqCsdShareholder("38501010002", "Jaan", "Tamm", new BigDecimal("100.00"));
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(shareholder), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson()
                .personalCode(PERSONAL_CODE)
                .shareholder(true)
                .ownershipPercent(new BigDecimal("100.00"))
                .build());
  }

  @Test
  void mapsLegalEntityOwnerAsNonNaturalPerson() {
    var legalEntityOwner =
        legalEntityShareholder("90000002", "Holding OÜ", new BigDecimal("100.00"));
    var detail =
        new CompanyDetail("Test OÜ", "12345678", "R", "OÜ", null, null, null, null, List.of());

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(legalEntityOwner), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson("90000002")
                .naturalPerson(false)
                .shareholder(true)
                .ownershipPercent(new BigDecimal("100.00"))
                .build());
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
