package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;
import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import ee.tuleva.onboarding.kyb.screener.CompanyStructureScreener;
import ee.tuleva.onboarding.kyb.screener.DualMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.SingleBoardMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.SoleMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class KybNasdaqCsdScreeningTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-03-27T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final AriregisterClient ariregisterClient = mock(AriregisterClient.class);
  private final LatestKybSurveyInputs latestKybSurveyInputs = mock(LatestKybSurveyInputs.class);

  private final KybCompanyDataMapper mapper = new KybCompanyDataMapper(amlCheckRepository);
  private final KybScreeningService screeningService =
      new KybScreeningService(
          List.of(
              new CompanyStructureScreener(),
              new SoleMemberOwnershipScreener(),
              new DualMemberOwnershipScreener(),
              new SingleBoardMemberOwnershipScreener()),
          mock(KybDataChangeDetector.class),
          mock(ApplicationEventPublisher.class),
          mock(KybCheckOverrideRepository.class),
          FIXED_CLOCK);
  private final LegalEntityScreener screener =
      new LegalEntityScreener(
          ariregisterClient, mapper, screeningService, latestKybSurveyInputs, FIXED_CLOCK);

  @Test
  void nasdaqCsdRegisteredSoleOwnerOuIsNotRejectedForStructureOrOwnership() {
    givenRelationships(nasdaqCsdSoleOwnerRelationships(JAAN.value()));
    givenBeneficialOwners(new BeneficialOwner("Jaan", "Tamm", JAAN.value(), "O"));

    var checks = screenActive(JAAN);

    assertThat(success(checks, COMPANY_STRUCTURE)).isTrue();
    assertThat(success(checks, SOLE_MEMBER_OWNERSHIP)).isTrue();
  }

  @Test
  void legalEntityShareholderIsRejectedForStructure() {
    givenRelationships(
        List.of(
            boardMember(JAAN.value(), "Jaan", "Tamm"),
            legalEntityShareholder("90000002", "Holding OÜ", new BigDecimal("100.00"))));
    givenBeneficialOwners();

    var checks = screenActive(JAAN);

    assertThat(success(checks, COMPANY_STRUCTURE)).isFalse();
  }

  @Test
  void twoNasdaqCsdBoardMemberOwnersPassDualOwnership() {
    givenRelationships(
        List.of(
            boardMember(JAAN.value(), "Jaan", "Tamm"),
            nasdaqCsdShareholder(JAAN.value(), "Jaan", "Tamm", new BigDecimal("50.00")),
            nasdaqBeneficialOwner(JAAN.value(), "Jaan", "Tamm"),
            boardMember(MARI.value(), "Mari", "Kask"),
            nasdaqCsdShareholder(MARI.value(), "Mari", "Kask", new BigDecimal("50.00")),
            nasdaqBeneficialOwner(MARI.value(), "Mari", "Kask"),
            shareRegistrar()));
    givenBeneficialOwners(
        new BeneficialOwner("Jaan", "Tamm", JAAN.value(), "O"),
        new BeneficialOwner("Mari", "Kask", MARI.value(), "O"));

    var checks = screenActive(JAAN);

    assertThat(success(checks, COMPANY_STRUCTURE)).isTrue();
    assertThat(success(checks, DUAL_MEMBER_OWNERSHIP)).isTrue();
  }

  private void givenBeneficialOwners(BeneficialOwner... owners) {
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE))
        .willReturn(new BeneficialOwners(List.of(owners), 0));
  }

  private void givenRelationships(List<CompanyRelationship> relationships) {
    given(
            ariregisterClient.getActiveCompanyRelationships(
                REGISTRY_CODE, LocalDate.now(FIXED_CLOCK)))
        .willReturn(relationships);
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE))
        .willReturn(Optional.of(VALID_COMPANY_DETAIL));
  }

  private List<KybCheck> screenActive(PersonalCode owner) {
    var relationships = screener.fetchActiveRelationships(REGISTRY_CODE);
    return screener.validate(REGISTRY_CODE, owner, null, relationships).checks();
  }

  private static boolean success(List<KybCheck> checks, KybCheckType type) {
    return checks.stream().filter(c -> c.type() == type).findFirst().orElseThrow().success();
  }
}
