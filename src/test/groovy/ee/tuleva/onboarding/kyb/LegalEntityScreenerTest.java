package ee.tuleva.onboarding.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.survey.KybSurveyInputs;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalEntityScreenerTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final PersonalCode PERSONAL_CODE = new PersonalCode("38501010002");
  private static final SelfCertification SELF_CERT = new SelfCertification(true, true, true);
  private static final BeneficialOwners BENEFICIAL_OWNERS =
      new BeneficialOwners(List.of(new BeneficialOwner("Jaan", "Tamm", "38501010002", "O")), 0);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-03-27T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final AriregisterClient ariregisterClient = mock(AriregisterClient.class);
  private final KybCompanyDataMapper kybCompanyDataMapper = mock(KybCompanyDataMapper.class);
  private final KybScreeningService kybScreeningService = mock(KybScreeningService.class);
  private final LatestKybSurveyInputs latestKybSurveyInputs = mock(LatestKybSurveyInputs.class);

  private final LegalEntityScreener screener =
      new LegalEntityScreener(
          ariregisterClient,
          kybCompanyDataMapper,
          kybScreeningService,
          latestKybSurveyInputs,
          FIXED_CLOCK);

  @Test
  void fetchActiveRelationshipsKeepsOnlyOwnershipAndControlRoles() {
    var boardMember = relationship("JUHL", "Jaan", "Tamm", "38501010002");
    var registryShareholder = relationship("OSAN", "Peeter", "Osanik", "37601010003");
    var nasdaqShareholder = relationship("O", "Mari", "Kask", "49001010001");
    var beneficialOwner = relationship("W", "Jaan", "Tamm", "38501010002");
    var founder = relationship("A", "Mari", "Asutaja", "39901010001");
    var shareRegistrar = relationship("ORP", "Nasdaq", "CSD", "90000003");
    var stockRegistrar = relationship("ARP", "Nasdaq", "CSD", "90000004");
    var prokurist = relationship("PROK", "Peeter", "Prokurist", "37601010003");
    given(
            ariregisterClient.getActiveCompanyRelationships(
                REGISTRY_CODE, LocalDate.now(FIXED_CLOCK)))
        .willReturn(
            List.of(
                boardMember,
                registryShareholder,
                nasdaqShareholder,
                beneficialOwner,
                founder,
                shareRegistrar,
                stockRegistrar,
                prokurist));

    var result = screener.fetchActiveRelationships(REGISTRY_CODE);

    assertThat(result)
        .containsExactly(boardMember, registryShareholder, nasdaqShareholder, beneficialOwner);
  }

  @Test
  void screenBuildsCompanyDataAndDelegatesToKybScreeningService() {
    var relationships = List.of(relationship("JUHL", "Jaan", "Tamm", "38501010002"));
    var detail = sampleDetail();
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.of(detail));
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE)).willReturn(BENEFICIAL_OWNERS);
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", null, LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.R,
            List.of(),
            SELF_CERT,
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            null,
            List.of());
    given(
            kybCompanyDataMapper.toKybCompanyData(
                detail, PERSONAL_CODE, relationships, BENEFICIAL_OWNERS, SELF_CERT))
        .willReturn(companyData);
    var checks = List.of(new KybCheck(KybCheckType.COMPANY_ACTIVE, true, Map.of()));
    given(kybScreeningService.screen(companyData)).willReturn(checks);

    var result = screener.screen(REGISTRY_CODE, PERSONAL_CODE, SELF_CERT, relationships);

    assertThat(result).isEqualTo(checks);
    verify(kybScreeningService).screen(companyData);
  }

  @Test
  void validateReturnsDetailAndChecks() {
    var relationships = List.of(relationship("JUHL", "Jaan", "Tamm", "38501010002"));
    var detail = sampleDetail();
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.of(detail));
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE)).willReturn(BENEFICIAL_OWNERS);
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", null, LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.R,
            List.of(),
            null,
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            null,
            List.of());
    given(
            kybCompanyDataMapper.toKybCompanyData(
                detail, PERSONAL_CODE, relationships, BENEFICIAL_OWNERS, null))
        .willReturn(companyData);
    var checks = List.of(new KybCheck(KybCheckType.COMPANY_ACTIVE, true, Map.of()));
    given(kybScreeningService.validate(companyData)).willReturn(checks);

    var result = screener.validate(REGISTRY_CODE, PERSONAL_CODE, null, relationships);

    assertThat(result.detail()).isEqualTo(detail);
    assertThat(result.checks()).isEqualTo(checks);
  }

  @Test
  void screenLatestResolvesSurveyInputsAndRunsScreening() {
    var boardMember = relationship("JUHL", "Jaan", "Tamm", "38501010002");
    var founder = relationship("A", "Mari", "Asutaja", "39901010001");
    given(
            ariregisterClient.getActiveCompanyRelationships(
                REGISTRY_CODE, LocalDate.now(FIXED_CLOCK)))
        .willReturn(List.of(boardMember, founder));
    var detail = sampleDetail();
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.of(detail));
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE)).willReturn(BENEFICIAL_OWNERS);
    given(latestKybSurveyInputs.findByRegistryCode(REGISTRY_CODE))
        .willReturn(new KybSurveyInputs(PERSONAL_CODE, SELF_CERT));
    var companyData =
        new KybCompanyData(
            new CompanyDto(new RegistryCode(REGISTRY_CODE), "Test OÜ", null, LegalForm.OÜ),
            PERSONAL_CODE,
            CompanyStatus.R,
            List.of(),
            SELF_CERT,
            "EE",
            "Harju maakond, Tallinn, Pärnu mnt 1",
            null,
            List.of());
    given(
            kybCompanyDataMapper.toKybCompanyData(
                detail, PERSONAL_CODE, List.of(boardMember), BENEFICIAL_OWNERS, SELF_CERT))
        .willReturn(companyData);
    var checks = List.of(new KybCheck(KybCheckType.COMPANY_ACTIVE, true, Map.of()));
    given(kybScreeningService.screen(companyData)).willReturn(checks);

    var result = screener.screenLatest(REGISTRY_CODE);

    assertThat(result).isEqualTo(checks);
    verify(kybScreeningService).screen(companyData);
  }

  @Test
  void screenThrowsWhenCompanyNotFoundInAriregister() {
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> screener.screen(REGISTRY_CODE, PERSONAL_CODE, SELF_CERT, List.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void validateThrowsWhenCompanyNotFoundInAriregister() {
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> screener.validate(REGISTRY_CODE, PERSONAL_CODE, null, List.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static CompanyRelationship relationship(
      String roleCode, String firstName, String lastName, String personalCode) {
    return new CompanyRelationship(
        "F",
        roleCode,
        roleCode,
        firstName,
        lastName,
        personalCode,
        null,
        null,
        null,
        new BigDecimal("100.00"),
        null,
        "EST");
  }

  private static CompanyDetail sampleDetail() {
    return new CompanyDetail(
        "Test OÜ", REGISTRY_CODE, "R", "OÜ", null, null, null, null, List.of());
  }
}
