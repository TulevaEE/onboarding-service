package ee.tuleva.onboarding.kyb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.kyb.survey.KybCompanyDataMapper;
import ee.tuleva.onboarding.kyb.survey.KybSurveyDataProvider;
import ee.tuleva.onboarding.kyb.survey.KybSurveyDataProvider.SurveyData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KybMonitoringServiceTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");
  private static final SelfCertification SELF_CERT = new SelfCertification(true, true, true);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-03-27T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final AriregisterClient ariregisterClient = mock(AriregisterClient.class);
  private final KybCompanyDataMapper kybCompanyDataMapper = mock(KybCompanyDataMapper.class);
  private final KybSurveyDataProvider kybSurveyDataProvider = mock(KybSurveyDataProvider.class);
  private final KybScreeningService kybScreeningService = mock(KybScreeningService.class);
  private final CompanyRepository companyRepository = mock(CompanyRepository.class);

  private final KybMonitoringService service =
      new KybMonitoringService(
          ariregisterClient,
          kybCompanyDataMapper,
          kybSurveyDataProvider,
          kybScreeningService,
          companyRepository,
          FIXED_CLOCK);

  @Test
  void screensCompanyWithFreshAriregisterData() {
    var surveyData = new SurveyData(PERSONAL_CODE, SELF_CERT);
    given(kybSurveyDataProvider.getLatestByRegistryCode(REGISTRY_CODE)).willReturn(surveyData);

    var relationships = List.<CompanyRelationship>of();
    given(
            ariregisterClient.getActiveCompanyRelationships(
                REGISTRY_CODE, LocalDate.now(FIXED_CLOCK)))
        .willReturn(relationships);

    var detail = new CompanyDetail("Test OÜ", REGISTRY_CODE, "R", "OÜ", null, null, null, null);
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.of(detail));

    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", null, LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.R,
            List.of(),
            SELF_CERT);
    given(kybCompanyDataMapper.toKybCompanyData(detail, PERSONAL_CODE, relationships, SELF_CERT))
        .willReturn(companyData);

    service.screenCompany(REGISTRY_CODE);

    verify(kybScreeningService).screen(companyData);
  }

  @Test
  void throwsWhenNoSurveyFound() {
    given(kybSurveyDataProvider.getLatestByRegistryCode(REGISTRY_CODE))
        .willThrow(new IllegalStateException("No KYB survey found"));

    assertThatThrownBy(() -> service.screenCompany(REGISTRY_CODE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void throwsWhenCompanyNotFoundInAriregister() {
    given(kybSurveyDataProvider.getLatestByRegistryCode(REGISTRY_CODE))
        .willReturn(new SurveyData(PERSONAL_CODE, SELF_CERT));
    given(ariregisterClient.getActiveCompanyRelationships(eq(REGISTRY_CODE), any()))
        .willReturn(List.of());
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.screenCompany(REGISTRY_CODE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void screenAllCompaniesProcessesEachCompany() {
    var company1 = Company.builder().registryCode("11111111").name("Company 1").build();
    var company2 = Company.builder().registryCode("22222222").name("Company 2").build();
    given(companyRepository.findAll()).willReturn(List.of(company1, company2));

    given(kybSurveyDataProvider.getLatestByRegistryCode(any()))
        .willReturn(new SurveyData(PERSONAL_CODE, SELF_CERT));
    given(ariregisterClient.getActiveCompanyRelationships(any(), any())).willReturn(List.of());

    var detail1 = new CompanyDetail("Company 1", "11111111", "R", "OÜ", null, null, null, null);
    var detail2 = new CompanyDetail("Company 2", "22222222", "R", "OÜ", null, null, null, null);
    given(ariregisterClient.getCompanyDetails("11111111")).willReturn(Optional.of(detail1));
    given(ariregisterClient.getCompanyDetails("22222222")).willReturn(Optional.of(detail2));
    given(kybCompanyDataMapper.toKybCompanyData(any(), any(), any(), any()))
        .willReturn(mock(KybCompanyData.class));

    service.screenAllCompanies();

    verify(kybScreeningService, times(2)).screen(any());
  }

  @Test
  void screenAllCompaniesContinuesWhenOneCompanyFails() {
    var company1 = Company.builder().registryCode("11111111").name("Company 1").build();
    var company2 = Company.builder().registryCode("22222222").name("Company 2").build();
    given(companyRepository.findAll()).willReturn(List.of(company1, company2));

    given(kybSurveyDataProvider.getLatestByRegistryCode("11111111"))
        .willThrow(new IllegalStateException("No survey"));
    given(kybSurveyDataProvider.getLatestByRegistryCode("22222222"))
        .willReturn(new SurveyData(PERSONAL_CODE, SELF_CERT));

    var detail = new CompanyDetail("Company 2", "22222222", "R", "OÜ", null, null, null, null);
    given(ariregisterClient.getActiveCompanyRelationships(eq("22222222"), any()))
        .willReturn(List.of());
    given(ariregisterClient.getCompanyDetails("22222222")).willReturn(Optional.of(detail));
    given(kybCompanyDataMapper.toKybCompanyData(any(), any(), any(), any()))
        .willReturn(mock(KybCompanyData.class));

    service.screenAllCompanies();

    verify(kybScreeningService, times(1)).screen(any());
  }

  @Test
  void screenAllCompaniesHandlesEmptyCompanyList() {
    given(companyRepository.findAll()).willReturn(List.of());

    service.screenAllCompanies();

    verifyNoInteractions(kybScreeningService);
  }
}
