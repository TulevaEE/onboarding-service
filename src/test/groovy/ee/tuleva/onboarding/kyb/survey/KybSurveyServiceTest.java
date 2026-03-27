package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSource.*;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.WHITELISTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ariregister.AddressDetails;
import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyAddress;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.*;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSourceItem;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus;
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
  @Mock private KybSurveyResponseMapper kybSurveyResponseMapper;
  @Mock private KybSurveyRepository kybSurveyRepository;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-25T10:00:00Z"), ZoneId.of("UTC"));

  @InjectMocks private KybSurveyService service;

  @Test
  void initialValidation_returnsLegalEntityDataWithFieldErrors() {
    var foundingDate = LocalDate.of(2020, 1, 15);
    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            foundingDate,
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
    assertThat(result.foundingDate().value()).isEqualTo(foundingDate);
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

  @Test
  void initialValidation_deduplicatesRelatedPersons() {
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
    var relationships =
        List.of(
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
                null,
                null,
                "EST"),
            new CompanyRelationship(
                "F",
                "S",
                "Osanik",
                "JAAN",
                "TAMM",
                PERSONAL_CODE,
                null,
                null,
                null,
                new BigDecimal("100.00"),
                "Osaluse kaudu",
                "EST"));
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

    assertThat(result.relatedPersons().value()).hasSize(1);
    assertThat(result.relatedPersons().value().getFirst())
        .isEqualTo(new RelatedPersonData(PERSONAL_CODE, "Jaan Tamm"));
  }

  @Test
  void submit_runsScreeningWithSelfCertification() {
    var selfCert = new SelfCertification(true, true, true);
    var surveyResponse = sampleSurveyResponse();
    when(kybSurveyResponseMapper.extractSelfCertification(surveyResponse)).thenReturn(selfCert);

    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            null,
            new CompanyAddress("Tallinn", null),
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
            selfCert);
    when(kybCompanyDataMapper.toKybCompanyData(
            detail, new PersonalCode(PERSONAL_CODE), relationships, selfCert))
        .thenReturn(companyData);
    when(kybScreeningService.screen(companyData))
        .thenReturn(
            List.of(
                new KybCheck(COMPANY_ACTIVE, true, Map.of()),
                new KybCheck(SELF_CERTIFICATION, true, Map.of()),
                new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of())));
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse);

    verify(kybSurveyRepository).save(any(KybSurvey.class));
    verify(kybScreeningService).screen(companyData);
  }

  @Test
  void submit_throwsWhenPersonIsNotBoardMember() {
    var surveyResponse = sampleSurveyResponse();

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

    assertThatThrownBy(() -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse))
        .isInstanceOf(NotBoardMemberException.class);
  }

  @Test
  void initialValidation_returnsNameErrorWhenAlreadyOnboarded() {
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
        .thenReturn(List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(REGISTRY_CODE))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.COMPLETED));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors()).containsExactly("Ettevõte on juba liitunud");
  }

  @Test
  void initialValidation_returnsNameErrorWhenNotWhitelistedAfterCutoff() {
    doReturn(Instant.parse("2026-03-28T10:00:00Z")).when(clock).instant();
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
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.of(2026, 3, 28)))
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
        .thenReturn(List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(REGISTRY_CODE))
        .thenReturn(Optional.empty());

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors()).containsExactly("Ettevõttel ei ole eelheakskiitu");
  }

  @Test
  void initialValidation_returnsNoWhitelistErrorWhenWhitelisted() {
    doReturn(Instant.parse("2026-03-28T10:00:00Z")).when(clock).instant();
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
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.of(2026, 3, 28)))
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
        .thenReturn(List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(REGISTRY_CODE))
        .thenReturn(Optional.of(WHITELISTED));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors()).doesNotContain("Ettevõttel ei ole eelheakskiitu");
  }

  @Test
  void submit_throwsWhenNotWhitelistedAfterCutoff() {
    doReturn(Instant.parse("2026-03-28T10:00:00Z")).when(clock).instant();
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.of(2026, 3, 28)))
        .thenReturn(sampleRelationships());
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(REGISTRY_CODE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, sampleSurveyResponse()))
        .isInstanceOf(OnboardingNotAllowedException.class);
  }

  @Test
  void submit_throwsWhenAlreadyOnboarded() {
    when(ariregisterClient.getActiveCompanyRelationships(REGISTRY_CODE, LocalDate.now(clock)))
        .thenReturn(sampleRelationships());
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(savingsFundOnboardingRepository.findStatusByPersonalCode(REGISTRY_CODE))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.COMPLETED));

    assertThatThrownBy(
            () -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, sampleSurveyResponse()))
        .isInstanceOf(OnboardingNotAllowedException.class);
  }

  private KybSurveyResponse sampleSurveyResponse() {
    return new KybSurveyResponse(
        List.of(
            new CompanySourceOfIncome(
                List.of(
                    new CompanyIncomeSourceItem.Option(ONLY_ACTIVE_IN_ESTONIA),
                    new CompanyIncomeSourceItem.Option(
                        NOT_SANCTIONED_NOT_PROFITING_FROM_SANCTIONED_COUNTRIES),
                    new CompanyIncomeSourceItem.Option(NOT_IN_CRYPTO)))));
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
