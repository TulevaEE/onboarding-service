package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ariregister.*;
import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KybSurveyServiceTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String PERSONAL_CODE = "38501010002";

  @Mock private AriregisterClient ariregisterClient;
  @Mock private KybCompanyDataMapper kybCompanyDataMapper;
  @Mock private KybScreeningService kybScreeningService;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneId.of("UTC"));

  @InjectMocks private KybSurveyService service;

  @Test
  void initialValidation_returnsLegalEntityDataWithFieldErrors() {
    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            null,
            new CompanyAddress("Tallinn", new AddressDetails(null, null, null, null)),
            "Fondide valitsemine",
            "6630");
    var relationships = sampleRelationships();
    when(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).thenReturn(Optional.of(detail));
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.now(clock)))
        .thenReturn(relationships);
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", "6630", LegalForm.OÜ),
            new PersonalCode(PERSONAL_CODE),
            CompanyStatus.R,
            List.of(),
            null);
    when(kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(PERSONAL_CODE), relationships, null))
        .thenReturn(companyData);
    when(kybScreeningService.screen(companyData))
        .thenReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(HIGH_RISK_NACE, false, Map.of()),
                new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of())));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().value()).isEqualTo("Test OÜ");
    assertThat(result.name().errors()).isEmpty();
    assertThat(result.registryCode().value()).isEqualTo(REGISTRY_CODE);
    assertThat(result.legalForm().value()).isEqualTo("OÜ");
    assertThat(result.status().value()).isEqualTo(LegalEntityStatus.REGISTERED);
    assertThat(result.status().errors()).isEmpty();
    assertThat(result.address().value())
        .isEqualTo(new LegalEntityAddress("Tallinn", null, null, null, null));
    assertThat(result.businessActivity().value()).isEqualTo("Fondide valitsemine");
    assertThat(result.naceCode().value()).isEqualTo("6630");
    assertThat(result.naceCode().errors()).containsExactly("See tegevusala ei ole toetatud");
    assertThat(result.relatedPersons().value()).hasSize(1);
    assertThat(result.relatedPersons().value().getFirst().name()).isEqualTo("Jaan Tamm");
    assertThat(result.relatedPersons().errors())
        .containsExactly("Ettevõtte omandistruktuur ei ole toetatud");
  }

  @Test
  void initialValidation_noOwnershipErrorWhenAtLeastOneOwnershipCheckPasses() {
    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            null,
            new CompanyAddress("Tallinn", new AddressDetails(null, null, null, null)),
            "Fondide valitsemine",
            "6630");
    var relationships = sampleRelationships();
    when(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).thenReturn(Optional.of(detail));
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.now(clock)))
        .thenReturn(relationships);
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", "6630", LegalForm.OÜ),
            new PersonalCode(PERSONAL_CODE),
            CompanyStatus.R,
            List.of(),
            null);
    when(kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(PERSONAL_CODE), relationships, null))
        .thenReturn(companyData);
    when(kybScreeningService.screen(companyData))
        .thenReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of()),
                new KybCheck(DUAL_MEMBER_OWNERSHIP, true, Map.of())));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.relatedPersons().errors()).isEmpty();
  }

  @Test
  void initialValidation_returnsNoErrorsWhenAllChecksPassed() {
    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            null,
            new CompanyAddress("Tallinn", new AddressDetails(null, null, null, null)),
            "Fondide valitsemine",
            "6630");
    var relationships = sampleRelationships();
    when(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).thenReturn(Optional.of(detail));
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.now(clock)))
        .thenReturn(relationships);
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", "6630", LegalForm.OÜ),
            new PersonalCode(PERSONAL_CODE),
            CompanyStatus.R,
            List.of(),
            null);
    when(kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(PERSONAL_CODE), relationships, null))
        .thenReturn(companyData);
    when(kybScreeningService.screen(companyData))
        .thenReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of())));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.status().errors()).isEmpty();
    assertThat(result.naceCode().errors()).isEmpty();
    assertThat(result.relatedPersons().errors()).isEmpty();
    assertThat(result.name().errors()).isEmpty();
  }

  @Test
  void initialValidation_throwsWhenPersonIsNotBoardMember() {
    var relationships =
        List.of(
            new CompanyRelationship(
                "F",
                "JUHL",
                "Juhatuse liige",
                "Jaan",
                "Tamm",
                "39901010001",
                null,
                null,
                null,
                new BigDecimal("100.00"),
                "Osaluse kaudu",
                "EST"));
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.now(clock)))
        .thenReturn(relationships);

    assertThatThrownBy(() -> service.initialValidation(REGISTRY_CODE, PERSONAL_CODE))
        .isInstanceOf(NotBoardMemberException.class);
  }

  private List<CompanyRelationship> sampleRelationships() {
    return List.of(
        new CompanyRelationship(
            "F",
            "JUHL",
            "Juhatuse liige",
            "Jaan",
            "Tamm",
            PERSONAL_CODE,
            null,
            null,
            null,
            new BigDecimal("100.00"),
            "Osaluse kaudu",
            "EST"));
  }
}
