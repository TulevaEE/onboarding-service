package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.ariregister.AriregisterTestClients.client;
import static ee.tuleva.onboarding.ariregister.AriregisterTestClients.webServiceTemplate;
import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import ee.tuleva.onboarding.aml.KycChecks;
import ee.tuleva.onboarding.kyb.screener.CompanyNaceScreener;
import ee.tuleva.onboarding.kyb.screener.CompanyStructureScreener;
import ee.tuleva.onboarding.kyb.screener.DualMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.SingleBoardMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.screener.SoleMemberOwnershipScreener;
import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.ResourceSource;

class RegistryResponseScreeningIntegrationTest {

  private static final String REGISTRY_CODE = "99000001";
  private static final Map<String, String> NS = Map.of("ar", "http://arireg.x-road.eu/producer/");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private MockWebServiceServer registry;
  private LegalEntityScreener legalEntityScreener;

  @BeforeEach
  void setUp() throws Exception {
    var webServiceTemplate = webServiceTemplate();
    registry = MockWebServiceServer.createServer(webServiceTemplate);
    var screeningService =
        new KybScreeningService(
            List.of(
                new CompanyNaceScreener(),
                new CompanyStructureScreener(),
                new SoleMemberOwnershipScreener(),
                new DualMemberOwnershipScreener(),
                new SingleBoardMemberOwnershipScreener()),
            mock(KybDataChangeDetector.class),
            mock(ApplicationEventPublisher.class),
            mock(KybCheckOverrideRepository.class),
            CLOCK);
    legalEntityScreener =
        new LegalEntityScreener(
            client(webServiceTemplate),
            new KybCompanyDataMapper(mock(KycChecks.class)),
            screeningService,
            mock(LatestKybSurveyInputs.class),
            CLOCK);
  }

  @Test
  void rejectsACompanyWhoseMainActivityIsHighRisk() throws IOException {
    registryRespondsWithDetails("detailandmed_v2_response.xml");

    assertThat(activityCheck().success()).isFalse();
  }

  @Test
  void rejectsACompanyWhoseSecondaryActivityIsHighRisk() throws IOException {
    registryRespondsWithDetails("detailandmed_v2_secondary_high_risk_activity_response.xml");

    assertThat(activityCheck().success()).isFalse();
  }

  @Test
  void acceptsACompanyWhoseHighRiskActivityHasEnded() throws IOException {
    registryRespondsWithDetails("detailandmed_v2_ended_high_risk_activity_response.xml");

    assertThat(activityCheck().success()).isTrue();
  }

  @Test
  void acceptsTwoBoardMembersWhoOwnTheCompanysOnlyShareJointly() throws IOException {
    registryRespondsWithJointOwnership(
        "ettevottegaSeotudIsikud_v1_joint_ownership_two_board_members_response.xml");

    assertThat(ownershipChecks())
        .containsExactly(
            new KybCheck(DUAL_MEMBER_OWNERSHIP, true, Map.of("totalOwnership", "100.000000")));
  }

  @Test
  void acceptsAJointlyOwnedShareWhenOnlyOneOfItsOwnersSitsOnTheBoard() throws IOException {
    registryRespondsWithJointOwnership(
        "ettevottegaSeotudIsikud_v1_joint_ownership_one_board_member_response.xml");

    assertThat(ownershipChecks())
        .containsExactly(
            new KybCheck(
                SINGLE_BOARD_MEMBER_OWNERSHIP,
                true,
                Map.of("boardMemberPersonalCode", "38501010002", "totalOwnership", "100.000000")));
  }

  private List<KybCheck> ownershipChecks() {
    var relationships = legalEntityScreener.fetchActiveRelationships(REGISTRY_CODE);
    var checks =
        legalEntityScreener
            .validate(REGISTRY_CODE, new PersonalCode("38501010002"), null, relationships)
            .checks();
    registry.verify();
    return checks.stream().filter(check -> check.type().isOwnershipCheck()).toList();
  }

  private void registryRespondsWithJointOwnership(String relationshipsFixture) throws IOException {
    registryResponds("ettevottegaSeotudIsikud_v1", relationshipsFixture);
    registryResponds("detailandmed_v2", "detailandmed_v2_joint_ownership_response.xml");
    registryResponds("tegelikudKasusaajad_v2", "tegelikudKasusaajad_v2_joint_owners_response.xml");
  }

  private KybCheck activityCheck() {
    var checks =
        legalEntityScreener
            .validate(REGISTRY_CODE, new PersonalCode("38501010002"), null, List.of())
            .checks();
    registry.verify();
    return checks.stream()
        .filter(check -> check.type() == HIGH_RISK_NACE)
        .findFirst()
        .orElseThrow();
  }

  private void registryRespondsWithDetails(String detailsFixture) throws IOException {
    registryResponds("detailandmed_v2", detailsFixture);
    registryResponds("tegelikudKasusaajad_v2", "tegelikudKasusaajad_v2_empty_response.xml");
  }

  private void registryResponds(String operation, String fixture) throws IOException {
    registry
        .expect(xpath("//ar:" + operation, NS).exists())
        .andRespond(
            withPayload(new ResourceSource(new ClassPathResource("ariregister/" + fixture))));
  }
}
