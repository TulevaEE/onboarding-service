package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.RELATED_PERSONS_KYC;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.ariregister.AriregisterClient;
import ee.tuleva.onboarding.ariregister.BeneficialOwner;
import ee.tuleva.onboarding.ariregister.BeneficialOwners;
import ee.tuleva.onboarding.ariregister.CompanyDetail;
import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Transactional
class BeneficialOwnerScreeningIntegrationTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final PersonalCode OWNER = new PersonalCode("38501010002");

  @Autowired private LegalEntityScreener screener;
  @Autowired private JsonMapper objectMapper;
  @MockitoBean private AriregisterClient ariregisterClient;
  @MockitoBean private PepAndSanctionCheckService sanctionCheckService;

  @BeforeEach
  void setUp() {
    given(sanctionCheckService.matchCompany(any()))
        .willReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    given(ariregisterClient.getCompanyDetails(REGISTRY_CODE))
        .willReturn(
            Optional.of(
                new CompanyDetail(
                    "Test OÜ", REGISTRY_CODE, "R", "OÜ", null, null, null, null, List.of())));
    given(ariregisterClient.getActiveCompanyRelationships(eq(REGISTRY_CODE), any()))
        .willReturn(List.of(boardMemberRow(), shareholderRow()));
  }

  @Test
  void soleOwnerPassesOwnershipCheckWhenRegistryListsThemAsBeneficialOwner() {
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE))
        .willReturn(
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", OWNER.value(), "O")), 0));

    var results = screenCompany();

    assertThat(results).anyMatch(check -> check.type() == SOLE_MEMBER_OWNERSHIP && check.success());
  }

  @Test
  void soleOwnerFailsOwnershipCheckWhenRegistryListsNoBeneficialOwners() {
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE)).willReturn(BeneficialOwners.none());

    var results = screenCompany();

    assertThat(results)
        .anyMatch(check -> check.type() == SOLE_MEMBER_OWNERSHIP && !check.success());
  }

  @Test
  void hiddenBeneficialOwnerFailsRelatedPersonsKycCheck() {
    given(ariregisterClient.getBeneficialOwners(REGISTRY_CODE))
        .willReturn(
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", OWNER.value(), "O")), 1));

    var results = screenCompany();

    assertThat(results).anyMatch(check -> check.type() == RELATED_PERSONS_KYC && !check.success());
  }

  private List<KybCheck> screenCompany() {
    return screener.screen(
        REGISTRY_CODE,
        OWNER,
        new SelfCertification(true, true, true),
        screener.fetchActiveRelationships(REGISTRY_CODE));
  }

  private static CompanyRelationship boardMemberRow() {
    return new CompanyRelationship(
        "F",
        "JUHL",
        "Juhatuse liige",
        "Jaan",
        "Tamm",
        OWNER.value(),
        LocalDate.of(1985, 1, 1),
        LocalDate.of(2020, 1, 1),
        null,
        null,
        null,
        "EST");
  }

  private static CompanyRelationship shareholderRow() {
    return new CompanyRelationship(
        "F",
        "OSAN",
        "Osanik",
        "Jaan",
        "Tamm",
        OWNER.value(),
        LocalDate.of(1985, 1, 1),
        LocalDate.of(2020, 1, 1),
        null,
        new BigDecimal("100.00"),
        null,
        "EST");
  }
}
