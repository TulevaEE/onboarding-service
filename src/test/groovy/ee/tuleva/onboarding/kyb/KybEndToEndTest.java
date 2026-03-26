package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.*;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Transactional
class KybEndToEndTest {

  @Autowired private KybScreeningService kybScreeningService;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private JsonMapper objectMapper;
  @MockitoBean private PepAndSanctionCheckService sanctionCheckService;

  @BeforeEach
  void setUp() {
    when(sanctionCheckService.matchCompany(any())).thenReturn(emptyMatchResponse());
  }

  // --- Rule 31: Single person OÜ ownership ---

  @Test
  void rule31_soleOwnerBoardMemberBeneficialOwner_allChecksPass() {
    var results = kybScreeningService.screen(rule31Pass());

    assertThat(results).hasSize(9).allMatch(KybCheck::success);
    assertCheckPersisted(JAAN, KYB_SOLE_MEMBER_OWNERSHIP, true);
  }

  @Test
  void rule31_notBeneficialOwner_ownershipCheckFails() {
    var results = kybScreeningService.screen(rule31Fail_notBeneficialOwner());

    assertCheckResult(results, SOLE_MEMBER_OWNERSHIP, false);
    assertCheckPersisted(JAAN, KYB_SOLE_MEMBER_OWNERSHIP, false);
  }

  // --- Rule 32: Two person OÜ ownership (both board members) ---

  @Test
  void rule32_twoBoardMembersFullOwnership_passes() {
    var results = kybScreeningService.screen(rule32Pass());

    assertCheckResult(results, DUAL_MEMBER_OWNERSHIP, true);
    assertCheckPersisted(JAAN, KYB_DUAL_MEMBER_OWNERSHIP, true);
  }

  @Test
  void rule32_twoBoardMembersIncompleteOwnership_fails() {
    var results = kybScreeningService.screen(rule32Fail_incompleteOwnership());

    assertCheckResult(results, DUAL_MEMBER_OWNERSHIP, false);
    assertCheckPersisted(JAAN, KYB_DUAL_MEMBER_OWNERSHIP, false);
  }

  // --- Rule 33: Sole board member is owner ---

  @Test
  void rule33_soleBoardMemberIsOwner_passes() {
    var results = kybScreeningService.screen(rule33Pass());

    assertCheckResult(results, SOLE_BOARD_MEMBER_IS_OWNER, true);
    assertCheckPersisted(JAAN, KYB_SOLE_BOARD_MEMBER_IS_OWNER, true);
  }

  @Test
  void rule33_soleBoardMemberIsNotOwner_fails() {
    var results = kybScreeningService.screen(rule33Fail_boardMemberNotOwner());

    assertCheckResult(results, SOLE_BOARD_MEMBER_IS_OWNER, false);
    assertCheckPersisted(JAAN, KYB_SOLE_BOARD_MEMBER_IS_OWNER, false);
  }

  // --- Rule 34: Company active ---

  @Test
  void rule34_activeCompany_passes() {
    var results = kybScreeningService.screen(rule34Pass());

    assertCheckResult(results, COMPANY_ACTIVE, true);
    assertCheckPersisted(JAAN, KYB_COMPANY_ACTIVE, true);
  }

  @Test
  void rule34_companyInLiquidation_fails() {
    var results = kybScreeningService.screen(rule34Fail_companyInLiquidation());

    assertCheckResult(results, COMPANY_ACTIVE, false);
    assertCheckPersisted(JAAN, KYB_COMPANY_ACTIVE, false);
  }

  // --- Rule 35: Data change detection ---

  @Test
  @SuppressWarnings("unchecked")
  void rule35_companyDataChangedBetweenScreenings_detected() {
    kybScreeningService.screen(rule31Pass());

    var secondResults = kybScreeningService.screen(rule34Fail_companyInLiquidation());

    var dataChangedCheck = secondResults.stream().filter(c -> c.type() == DATA_CHANGED).findFirst();
    assertThat(dataChangedCheck).isPresent();
    assertThat(dataChangedCheck.get().success()).isFalse();
    var changes = (List<Map<String, Object>>) dataChangedCheck.get().metadata().get("changes");
    assertThat(changes).anyMatch(c -> "COMPANY_ACTIVE".equals(c.get("check")));
  }

  // --- Rule 36: Related person not Estonian citizen or resident ---

  @Test
  void rule36_relatedPersonNotCitizen_kycRejected_fails() {
    var results = kybScreeningService.screen(rule36Fail_relatedPersonNotCitizen());

    assertCheckResult(results, RELATED_PERSONS_KYC, false);
    assertCheckPersisted(JAAN, KYB_RELATED_PERSONS_KYC, false);
  }

  // --- Rule 37: Related person from high-risk country ---

  @Test
  void rule37_relatedPersonHighRiskCountry_kycRejected_fails() {
    var results = kybScreeningService.screen(rule37Fail_relatedPersonHighRiskCountry());

    assertCheckResult(results, RELATED_PERSONS_KYC, false);
  }

  // --- Rule 39: Related person sanctioned ---

  @Test
  void rule39_relatedPersonSanctioned_kycRejected_fails() {
    var results = kybScreeningService.screen(rule39Fail_relatedPersonSanctioned());

    assertCheckResult(results, RELATED_PERSONS_KYC, false);
  }

  // --- Rule 40: Related person not citizen but is resident ---

  @Test
  void rule40_relatedPersonNotCitizenButResident_kycUnknown_fails() {
    var results = kybScreeningService.screen(rule40Fail_relatedPersonNotCitizenButResident());

    assertCheckResult(results, RELATED_PERSONS_KYC, false);
  }

  // --- Rules 36-40: All related persons passed KYC ---

  @Test
  void rules36to40_allRelatedPersonsPassedKyc_passes() {
    var results = kybScreeningService.screen(rules36to40Pass_allKycCompleted());

    assertCheckResult(results, RELATED_PERSONS_KYC, true);
  }

  @Test
  void rules36to40_kycStatusLookedUpFromDatabase() {
    amlCheckRepository.save(
        AmlCheck.builder()
            .personalCode(JAAN.value())
            .type(AmlCheckType.KYC_CHECK)
            .success(true)
            .metadata(Map.of())
            .build());
    amlCheckRepository.save(
        AmlCheck.builder()
            .personalCode(MARI.value())
            .type(AmlCheckType.KYC_CHECK)
            .success(false)
            .metadata(Map.of())
            .build());

    var person1 =
        person(JAAN, true, true, true, java.math.BigDecimal.valueOf(50), KybKycStatus.COMPLETED);
    var person2 =
        person(MARI, true, true, true, java.math.BigDecimal.valueOf(50), KybKycStatus.REJECTED);
    var data =
        new KybCompanyData(
            VALID_COMPANY, JAAN, CompanyStatus.R, List.of(person1, person2), VALID_CERT);

    var results = kybScreeningService.screen(data);

    assertCheckResult(results, RELATED_PERSONS_KYC, false);
  }

  // --- Rule 41: High-risk NACE code ---

  @Test
  void rule41_safeNaceCode_passes() {
    var results = kybScreeningService.screen(rule41Pass());

    assertCheckResult(results, HIGH_RISK_NACE, true);
  }

  @Test
  void rule41_highRiskNaceCode_fails() {
    var results = kybScreeningService.screen(rule41Fail_highRiskNace());

    assertCheckResult(results, HIGH_RISK_NACE, false);
    assertCheckPersisted(JAAN, KYB_HIGH_RISK_NACE, false);
  }

  // --- Rule 43: Company sanctions ---

  @Test
  void rule43_noSanctionsMatch_passes() {
    var results = kybScreeningService.screen(rule43Pass());

    assertCheckResult(results, COMPANY_SANCTION, true);
    assertCheckPersisted(JAAN, KYB_COMPANY_SANCTION, true);
  }

  @Test
  void rule43_companySanctioned_fails() {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(matchResponseWithTopic("sanction", "ru"));

    var results = kybScreeningService.screen(rule43Pass());

    assertCheckResult(results, COMPANY_SANCTION, false);
    assertCheckPersisted(JAAN, KYB_COMPANY_SANCTION, false);
  }

  // --- Rule 44: EU PEP (medium risk, passes) ---

  @Test
  void rule44_companyConnectedToEuPep_passes() {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(matchResponseWithTopic("role.pep", "ee"));

    var results = kybScreeningService.screen(rule44Pass());

    assertCheckResult(results, COMPANY_PEP, true);
  }

  // --- Rule 45: Non-EU PEP ---

  @Test
  void rule45_noPepMatch_passes() {
    var results = kybScreeningService.screen(rule45Pass());

    assertCheckResult(results, COMPANY_PEP, true);
    assertCheckPersisted(JAAN, KYB_COMPANY_PEP, true);
  }

  @Test
  void rule45_companyConnectedToNonEuPep_fails() {
    when(sanctionCheckService.matchCompany(any()))
        .thenReturn(matchResponseWithTopic("role.pep", "ru"));

    var results = kybScreeningService.screen(rule45Pass());

    assertCheckResult(results, COMPANY_PEP, false);
    assertCheckPersisted(JAAN, KYB_COMPANY_PEP, false);
  }

  // --- Rule 50: Legal form ---

  @Test
  void rule50_legalFormIsOÜ_passes() {
    var results = kybScreeningService.screen(rule50Pass());

    assertCheckResult(results, COMPANY_LEGAL_FORM, true);
  }

  @Test
  void rule50_legalFormIsNotOÜ_fails() {
    var results = kybScreeningService.screen(rule50Fail_notOÜ());

    assertCheckResult(results, COMPANY_LEGAL_FORM, false);
    assertCheckPersisted(JAAN, KYB_COMPANY_LEGAL_FORM, false);
  }

  // --- Self certification ---

  @Test
  void selfCertification_allConfirmed_passes() {
    var results = kybScreeningService.screen(selfCertificationPass());

    assertCheckResult(results, SELF_CERTIFICATION, true);
    assertCheckPersisted(JAAN, KYB_SELF_CERTIFICATION, true);
  }

  @Test
  void selfCertification_notConfirmed_fails() {
    var results = kybScreeningService.screen(selfCertificationFail());

    assertCheckResult(results, SELF_CERTIFICATION, false);
    assertCheckPersisted(JAAN, KYB_SELF_CERTIFICATION, false);
  }

  // --- Special case: >2 related persons ---

  @Test
  void threeRelatedPersons_noOwnershipCheckApplies() {
    var results = kybScreeningService.screen(threeRelatedPersons());

    var types = results.stream().map(KybCheck::type).toList();
    assertThat(types)
        .doesNotContain(SOLE_MEMBER_OWNERSHIP, DUAL_MEMBER_OWNERSHIP, SOLE_BOARD_MEMBER_IS_OWNER);
    assertCheckResult(results, COMPANY_ACTIVE, true);
    assertCheckResult(results, RELATED_PERSONS_KYC, true);
  }

  // --- Special case: all checks persisted ---

  @Test
  void allChecksPersistedToAmlCheckRepository() {
    kybScreeningService.screen(rule31Pass());

    var amlChecks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(JAAN.value(), aYearAgo());
    assertThat(amlChecks)
        .extracting(AmlCheck::getType)
        .containsExactlyInAnyOrder(
            KYB_SOLE_MEMBER_OWNERSHIP,
            KYB_COMPANY_ACTIVE,
            KYB_RELATED_PERSONS_KYC,
            KYB_COMPANY_SANCTION,
            KYB_COMPANY_PEP,
            KYB_HIGH_RISK_NACE,
            KYB_COMPANY_LEGAL_FORM,
            KYB_SELF_CERTIFICATION,
            KYB_DATA_CHANGED);
    assertThat(amlChecks).allMatch(AmlCheck::isSuccess);
  }

  // --- Helpers ---

  private void assertCheckResult(List<KybCheck> results, KybCheckType type, boolean expected) {
    var check = results.stream().filter(c -> c.type() == type).findFirst();
    assertThat(check).as("Expected check %s to be present", type).isPresent();
    assertThat(check.get().success())
        .as("Expected check %s success=%s", type, expected)
        .isEqualTo(expected);
  }

  private void assertCheckPersisted(
      PersonalCode personalCode, AmlCheckType type, boolean expectedSuccess) {
    var checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            personalCode.value(), aYearAgo());
    var matching = checks.stream().filter(c -> c.getType() == type).findFirst();
    assertThat(matching).as("Expected AmlCheck %s to be persisted", type).isPresent();
    assertThat(matching.get().isSuccess()).isEqualTo(expectedSuccess);
  }

  private MatchResponse emptyMatchResponse() {
    return new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
  }

  private MatchResponse matchResponseWithTopic(String topic, String country) {
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode node = objectMapper.createObjectNode();
    node.put("match", true);
    node.put("id", "Q123");
    ObjectNode properties = objectMapper.createObjectNode();
    ArrayNode topics = objectMapper.createArrayNode();
    topics.add(topic);
    properties.set("topics", topics);
    ArrayNode countries = objectMapper.createArrayNode();
    countries.add(country);
    properties.set("country", countries);
    node.set("properties", properties);
    results.add(node);
    return new MatchResponse(results, objectMapper.createObjectNode());
  }
}
