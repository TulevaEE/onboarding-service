package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.KycChecks;
import ee.tuleva.onboarding.ariregister.AddressDetails;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyAddress;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.ariregister.Shareholding;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KybCompanyDataMapperTest {

  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38888888888");
  private static final SelfCertification SELF_CERT = new SelfCertification(true, true, true);
  private static final BeneficialOwners NO_BENEFICIAL_OWNERS = BeneficialOwners.none();

  private final KycChecks kycChecks = mock(KycChecks.class);
  private final KybCompanyDataMapper mapper = new KybCompanyDataMapper(kycChecks);

  @Test
  void mapsPersonWithMultipleRolesToSingleRelatedPerson() {
    var boardMember =
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            "38888888888",
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
            "38888888888",
            LocalDate.of(1985, 1, 1),
            LocalDate.of(2020, 1, 1),
            null,
            new BigDecimal("100.00"),
            "Osaluse kaudu",
            "EST");

    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(boardMember, shareholder),
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", PERSONAL_CODE.value(), "O")), 0),
            SELF_CERT);

    assertThat(result.company())
        .isEqualTo(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", List.of(), LegalForm.OÜ));
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
  void mapsManagingDirectorAsBoardMember() {
    var managingBoardMember =
        new CompanyRelationship(
            "F",
            "JUHJ",
            "juhatuse liige (juhataja)",
            "Jaan",
            "Tamm",
            "38888888888",
            null,
            LocalDate.of(2006, 1, 2),
            null,
            null,
            null,
            "EST");

    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(managingBoardMember), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(kybPerson().personalCode(PERSONAL_CODE).boardMember(true).build());
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
            "38888888888",
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

    var detail = companyDetail().build();

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
            "38888888888",
            null,
            null,
            null,
            new BigDecimal("75.00"),
            "Osaluse kaudu",
            "EST");

    var detail = companyDetail().build();

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
            "38888888888",
            null,
            null,
            null,
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail,
            PERSONAL_CODE,
            List.of(relationship),
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", "38888888888", "O")), 0),
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
            "38888888888",
            null,
            null,
            null,
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail = companyDetail().build();

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
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(), new BeneficialOwners(List.of(), 2), SELF_CERT);

    assertThat(result.relatedPersons())
        .containsExactly(
            kybPerson().beneficialOwner(true).build(), kybPerson().beneficialOwner(true).build());
  }

  @Test
  void synthesizesUnidentifiedPersonForForeignBeneficialOwnerWithoutCode() {
    var detail = companyDetail().build();

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
    var detail = companyDetail().address(address).build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.countryCode()).isEqualTo("EE");
    assertThat(result.fullAddress()).isEqualTo("Tartu maakond, Tartu linn, Paju 2");
  }

  @Test
  void mapsNullAddressToNullCountryCodeAndFullAddress() {
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.countryCode()).isNull();
    assertThat(result.fullAddress()).isNull();
  }

  @Test
  void mapsCompanyStatus() {
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.status()).isEqualTo(R);
  }

  @Test
  void mapsFoundingDate() {
    var detail = companyDetail().foundingDate(LocalDate.of(2020, 1, 15)).build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.foundingDate()).isEqualTo(LocalDate.of(2020, 1, 15));
  }

  @Test
  void mapsNullFoundingDateWhenAriregisterHasNone() {
    var detail = companyDetail().build();

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
            "38888888888",
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

    var detail = companyDetail().build();

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
    var detail = companyDetail().name("Test TÜH").legalForm("TÜH").build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.company().legalForm()).isEqualTo(LegalForm.TÜH);
  }

  @Test
  void mapsUnknownLegalFormToOther() {
    var detail = companyDetail().name("Test XYZ").legalForm("XYZ").build();

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
            "38888888888",
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
            "38888888888",
            null,
            null,
            null,
            new BigDecimal("100.00"),
            null,
            "EST");

    var detail = companyDetail().build();

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
    when(kycChecks.latestKycCheckPassedWithinLastYear("38888888888")).thenReturn(Optional.of(true));

    var relationship = boardMemberRelationship("38888888888");
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.COMPLETED);
  }

  @Test
  void resolvesRejectedKycStatusFromDatabase() {
    when(kycChecks.latestKycCheckPassedWithinLastYear("38888888888"))
        .thenReturn(Optional.of(false));

    var relationship = boardMemberRelationship("38888888888");
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.REJECTED);
  }

  @Test
  void resolvesUnknownKycStatusWhenNoCheckExists() {
    var relationship = boardMemberRelationship("38888888888");
    var detail = companyDetail().build();

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, List.of(relationship), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.relatedPersons().getFirst().kycStatus()).isEqualTo(KybKycStatus.UNKNOWN);
  }

  @Test
  void mapsNasdaqCsdShareholderRoleToShareholder() {
    var shareholder = nasdaqCsdShareholder("38888888888", "Jaan", "Tamm", new BigDecimal("100.00"));
    var detail = companyDetail().build();

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
    var detail = companyDetail().build();

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

  @Test
  void companyCarriesTheCodesOfItsActivities() {
    var detail = companyDetail().activityCode("66301").activityCode("70201").build();

    var result =
        mapper.toKybCompanyData(detail, PERSONAL_CODE, List.of(), NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.company().activityCodes()).containsExactly("66301", "70201");
  }

  @Test
  void totalOwnershipAddsUpTheSharesTheScreenedShareholdersHold() {
    var detail =
        companyDetail()
            .shareholding(new Shareholding(new BigDecimal("60.00"), List.of(JAAN.value())))
            .shareholding(new Shareholding(new BigDecimal("40.00"), List.of(MARI.value())))
            .build();
    var relationships =
        List.of(
            shareholder(JAAN.value(), "Jaan", "Tamm", new BigDecimal("60.00")),
            shareholder(MARI.value(), "Mari", "Kask", new BigDecimal("40.00")));

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, relationships, NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.totalOwnership()).isEqualByComparingTo("100");
  }

  @Test
  void totalOwnershipCountsAJointlyOwnedShareOnceAlthoughEachOwnerIsListedWithAllOfIt() {
    var detail =
        companyDetail()
            .shareholding(
                new Shareholding(new BigDecimal("100.00"), List.of(JAAN.value(), MARI.value())))
            .build();
    var relationships =
        List.of(
            shareholder(JAAN.value(), "Jaan", "Tamm", new BigDecimal("100.00")),
            shareholder(MARI.value(), "Mari", "Kask", new BigDecimal("100.00")));

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, relationships, NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.totalOwnership()).isEqualByComparingTo("100");
  }

  @Test
  void totalOwnershipLeavesOutAShareHeldByAnyoneWhoWasNotScreenedAsAShareholder() {
    var detail =
        companyDetail()
            .shareholding(new Shareholding(new BigDecimal("60.00"), List.of(JAAN.value())))
            .shareholding(
                new Shareholding(new BigDecimal("40.00"), List.of(MARI.value(), PEETER.value())))
            .build();
    var relationships =
        List.of(
            shareholder(JAAN.value(), "Jaan", "Tamm", new BigDecimal("60.00")),
            shareholder(MARI.value(), "Mari", "Kask", new BigDecimal("40.00")));

    var result =
        mapper.toKybCompanyData(
            detail, PERSONAL_CODE, relationships, NO_BENEFICIAL_OWNERS, SELF_CERT);

    assertThat(result.totalOwnership()).isEqualByComparingTo("60");
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
