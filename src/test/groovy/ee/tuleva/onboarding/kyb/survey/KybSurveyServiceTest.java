package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSource.*;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ariregister.AddressDetails;
import ee.tuleva.onboarding.ariregister.CompanyAddress;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCheckType;
import ee.tuleva.onboarding.kyb.LegalEntityScreener;
import ee.tuleva.onboarding.kyb.LegalEntityScreener.ValidationResult;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanyIncomeSourceItem;
import ee.tuleva.onboarding.kyb.survey.KybSurveyResponseItem.CompanySourceOfIncome;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class KybSurveyServiceTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String PERSONAL_CODE = "38501010002";

  @Mock private LegalEntityScreener legalEntityScreener;
  @Mock private KybSurveyResponseMapper kybSurveyResponseMapper;
  @Mock private KybSurveyRepository kybSurveyRepository;
  @Mock private SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private KybSurveyService service;

  @BeforeEach
  void setUp() {
    service =
        new KybSurveyService(
            legalEntityScreener,
            kybSurveyResponseMapper,
            kybSurveyRepository,
            savingsFundOnboardingRepository,
            eventPublisher);
  }

  private CompanyDetail sampleDetail() {
    return new CompanyDetail(
        "Test OÜ",
        REGISTRY_CODE,
        "R",
        "OÜ",
        null,
        new CompanyAddress("Tallinn", new AddressDetails(null, null, null, null)),
        "Fondide valitsemine",
        "6630");
  }

  private void stubInitialValidation(
      List<CompanyRelationship> relationships, CompanyDetail detail, List<KybCheck> checks) {
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);
    when(legalEntityScreener.validate(
            REGISTRY_CODE, new PersonalCode(PERSONAL_CODE), null, relationships))
        .thenReturn(new ValidationResult(detail, checks));
  }

  @Test
  void initialValidation_returnsLegalEntityDataWithFieldErrors() {
    var detail =
        new CompanyDetail(
            "Test OÜ",
            REGISTRY_CODE,
            "R",
            "OÜ",
            java.time.LocalDate.of(2020, 1, 15),
            new CompanyAddress("Tallinn", new AddressDetails(null, null, null, null)),
            "Fondide valitsemine",
            "6630");
    var relationships = sampleRelationships();
    stubInitialValidation(
        relationships,
        detail,
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of()),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, false, Map.of())));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().value()).isEqualTo("Test OÜ");
    assertThat(result.name().errors()).isEmpty();
    assertThat(result.registryCode().value()).isEqualTo(REGISTRY_CODE);
    assertThat(result.legalForm().value()).isEqualTo("OÜ");
    assertThat(result.foundingDate().value()).isEqualTo(java.time.LocalDate.of(2020, 1, 15));
    assertThat(result.status().value()).isEqualTo(LegalEntityStatus.REGISTERED);
    assertThat(result.status().errors()).isEmpty();
    assertThat(result.address().value())
        .isEqualTo(new LegalEntityAddress("Tallinn", null, null, null, null));
    assertThat(result.businessActivity().value()).isEqualTo("Fondide valitsemine");
    assertThat(result.naceCode().value()).isEqualTo("6630");
    assertThat(result.naceCode().errors())
        .containsExactly(new ValidationError("UNSUPPORTED_NACE", "See tegevusala ei ole toetatud"));
    assertThat(result.relatedPersons().value()).hasSize(1);
    assertThat(result.relatedPersons().value().getFirst().name()).isEqualTo("Jaan Tamm");
    assertThat(result.relatedPersons().errors())
        .containsExactly(
            new ValidationError("COMPANY_STRUCTURE", "Ettevõtte omandistruktuur ei ole toetatud"));
  }

  @Test
  void initialValidation_codesOtherRelatedPersonsKycAndNamesThem() {
    stubInitialValidation(
        boardMemberWithTwoOwners(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(
                        Map.of("personalCode", "38501010003", "kycStatus", "PENDING"),
                        Map.of("personalCode", "38501010004", "kycStatus", "UNKNOWN"))))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.relatedPersons().errors())
        .containsExactly(
            new ValidationError(
                "OTHER_RELATED_PERSONS_KYC",
                "Isikusamasuse tuvastamine on lõpetamata: Mari Maasikas, Peeter Kask"));
  }

  @Test
  void initialValidation_codesUserKycWithoutNameWhenOwnKycIncomplete() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(Map.of("personalCode", PERSONAL_CODE, "kycStatus", "PENDING"))))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.relatedPersons().errors())
        .containsExactly(
            new ValidationError("USER_KYC", "Sinu isikusamasuse tuvastamine on lõpetamata"));
  }

  @Test
  void initialValidation_splitsUserAndOtherKycIntoSeparateErrors() {
    stubInitialValidation(
        boardMemberWithTwoOwners(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(
                RELATED_PERSONS_KYC,
                false,
                Map.of(
                    "incompletePersons",
                    List.of(
                        Map.of("personalCode", PERSONAL_CODE, "kycStatus", "PENDING"),
                        Map.of("personalCode", "38501010003", "kycStatus", "PENDING"),
                        Map.of("personalCode", "38501010004", "kycStatus", "UNKNOWN"))))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.relatedPersons().errors())
        .containsExactly(
            new ValidationError("USER_KYC", "Sinu isikusamasuse tuvastamine on lõpetamata"),
            new ValidationError(
                "OTHER_RELATED_PERSONS_KYC",
                "Isikusamasuse tuvastamine on lõpetamata: Mari Maasikas, Peeter Kask"));
  }

  private List<CompanyRelationship> boardMemberWithTwoOwners() {
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
            new BigDecimal("34.00"),
            null,
            "EST"),
        new CompanyRelationship(
            "F",
            "OSANIK",
            "Osanik",
            "Mari",
            "Maasikas",
            "38501010003",
            null,
            null,
            null,
            new BigDecimal("33.00"),
            null,
            "EST"),
        new CompanyRelationship(
            "F",
            "OSANIK",
            "Osanik",
            "Peeter",
            "Kask",
            "38501010004",
            null,
            null,
            null,
            new BigDecimal("33.00"),
            null,
            "EST"));
  }

  @Test
  void initialValidation_collapsesSanctionAndPepToIndistinguishableNameError() {
    var sanctionErrors = nameErrorsFor(COMPANY_SANCTION);
    var pepErrors = nameErrorsFor(COMPANY_PEP);

    assertThat(sanctionErrors).isEqualTo(pepErrors);
    assertThat(sanctionErrors)
        .containsExactly(
            new ValidationError("UNSERVICEABLE", "Ettevõtet ei ole võimalik teenindada"));
  }

  private List<ValidationError> nameErrorsFor(KybCheckType type) {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of()), new KybCheck(type, false, Map.of())));
    return service.initialValidation(REGISTRY_CODE, PERSONAL_CODE).name().errors();
  }

  @Test
  void initialValidation_carriesClientCodesForStatusAndLegalFormChecks() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, false, Map.of()),
            new KybCheck(COMPANY_LEGAL_FORM, false, Map.of())));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.status().errors())
        .containsExactly(new ValidationError("COMPANY_ACTIVE", "Ettevõte ei ole aktiivne"));
    assertThat(result.legalForm().errors())
        .containsExactly(new ValidationError("COMPANY_LEGAL_FORM", "Ainult OÜ on toetatud"));
  }

  @Test
  void initialValidation_returnsAddressErrorWhenNotRegisteredInEstonia() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, false, Map.of("countryCode", "DE"))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.address().errors())
        .containsExactly(
            new ValidationError(
                "COMPANY_REGISTERED_IN_ESTONIA", "Ettevõte ei ole registreeritud Eestis"));
  }

  @Test
  void initialValidation_returnsNoAddressErrorWhenRegisteredInEstonia() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_REGISTERED_IN_ESTONIA, true, Map.of("countryCode", "EE"))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.address().errors()).isEmpty();
  }

  @Test
  void initialValidation_returnsNoErrorsWhenAllChecksPassed() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
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
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);

    assertThatThrownBy(() -> service.initialValidation(REGISTRY_CODE, PERSONAL_CODE))
        .isInstanceOf(NotBoardMemberException.class);
  }

  @Test
  void initialValidation_publishesAuditEventWhenChecksFail() {
    var checks =
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(HIGH_RISK_NACE, false, Map.of("naceCode", "6630")));
    stubInitialValidation(sampleRelationships(), sampleDetail(), checks);

    service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, validationFailedAuditData(checks)));
  }

  @Test
  void initialValidation_doesNotPublishAuditEventWhenAllChecksPass() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(SOLE_MEMBER_OWNERSHIP, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of("changes", List.of("status changed")))));

    service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void initialValidation_doesNotPublishAuditEventWhenOnlyRiskSignalCheckFails() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(COMPANY_AGE, false, Map.of("foundingDate", "2026-03-01"))));

    service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void initialValidation_publishesAuditEventWhenNotBoardMember() {
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
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);

    assertThatThrownBy(() -> service.initialValidation(REGISTRY_CODE, PERSONAL_CODE))
        .isInstanceOf(NotBoardMemberException.class);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("NOT_BOARD_MEMBER")));
  }

  @Test
  void initialValidation_deduplicatesRelatedPersons() {
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
                "OSAN",
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
    stubInitialValidation(
        relationships,
        sampleDetail(),
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
    var relationships = sampleRelationships();
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse);

    verify(kybSurveyRepository).save(any(KybSurvey.class));
    verify(legalEntityScreener)
        .screen(REGISTRY_CODE, new PersonalCode(PERSONAL_CODE), selfCert, relationships);
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
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);

    assertThatThrownBy(() -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse))
        .isInstanceOf(NotBoardMemberException.class);
  }

  @Test
  void initialValidation_toleratesDataChangedCheck() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(
            new KybCheck(COMPANY_ACTIVE, true, Map.of()),
            new KybCheck(DATA_CHANGED, false, Map.of("changes", List.of("status changed")))));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors()).isEmpty();
  }

  @Test
  void initialValidation_allowsRejectedCompanyToRetry() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));
    when(savingsFundOnboardingRepository.findStatus(REGISTRY_CODE, LEGAL_ENTITY))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.REJECTED));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors()).isEmpty();
  }

  @Test
  void initialValidation_returnsNameErrorWhenAlreadyOnboarded() {
    stubInitialValidation(
        sampleRelationships(),
        sampleDetail(),
        List.of(new KybCheck(COMPANY_ACTIVE, true, Map.of())));
    when(savingsFundOnboardingRepository.findStatus(REGISTRY_CODE, LEGAL_ENTITY))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.COMPLETED));

    var result = service.initialValidation(REGISTRY_CODE, PERSONAL_CODE);

    assertThat(result.name().errors())
        .containsExactly(new ValidationError("ALREADY_ONBOARDED", "Ettevõte on juba liitunud"));
  }

  @Test
  void submit_throwsWhenAlreadyOnboarded() {
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE))
        .thenReturn(sampleRelationships());
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(savingsFundOnboardingRepository.findStatus(REGISTRY_CODE, LEGAL_ENTITY))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.COMPLETED));

    assertThatThrownBy(
            () -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, sampleSurveyResponse()))
        .isInstanceOf(OnboardingNotAllowedException.class);
  }

  @Test
  void submit_publishesAuditEventWhenNotBoardMember() {
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
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE)).thenReturn(relationships);

    assertThatThrownBy(() -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse))
        .isInstanceOf(NotBoardMemberException.class);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("NOT_BOARD_MEMBER")));
  }

  @Test
  void submit_publishesAuditEventWhenAlreadyOnboarded() {
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE))
        .thenReturn(sampleRelationships());
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(savingsFundOnboardingRepository.findStatus(REGISTRY_CODE, LEGAL_ENTITY))
        .thenReturn(Optional.of(SavingsFundOnboardingStatus.COMPLETED));

    assertThatThrownBy(
            () -> service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, sampleSurveyResponse()))
        .isInstanceOf(OnboardingNotAllowedException.class);

    verify(eventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE, blockedAuditData("ALREADY_ONBOARDED")));
  }

  @Test
  void submit_doesNotPublishAuditEventOnSuccessfulSubmission() {
    var selfCert = new SelfCertification(true, true, true);
    var surveyResponse = sampleSurveyResponse();
    when(kybSurveyResponseMapper.extractSelfCertification(surveyResponse)).thenReturn(selfCert);
    when(legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE))
        .thenReturn(sampleRelationships());
    when(kybSurveyRepository.save(any(KybSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.submit(1L, PERSONAL_CODE, REGISTRY_CODE, surveyResponse);

    verify(eventPublisher, never()).publishEvent(any());
  }

  private static Map<String, Object> validationFailedAuditData(List<KybCheck> checks) {
    var data = new java.util.LinkedHashMap<String, Object>();
    data.put("partyType", "LEGAL_ENTITY");
    data.put("registryCode", REGISTRY_CODE);
    data.put("personalCode", PERSONAL_CODE);
    data.put("outcome", "VALIDATION_FAILED");
    data.put(
        "checks",
        checks.stream()
            .map(
                c ->
                    Map.of(
                        "type", c.type().name(),
                        "success", c.success(),
                        "metadata", c.metadata()))
            .toList());
    return data;
  }

  private static Map<String, Object> blockedAuditData(String reason) {
    var data = new java.util.LinkedHashMap<String, Object>();
    data.put("partyType", "LEGAL_ENTITY");
    data.put("registryCode", REGISTRY_CODE);
    data.put("personalCode", PERSONAL_CODE);
    data.put("outcome", "BLOCKED");
    data.put("blockedReason", reason);
    return data;
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
