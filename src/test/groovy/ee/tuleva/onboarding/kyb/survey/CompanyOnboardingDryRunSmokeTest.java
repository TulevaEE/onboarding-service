package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybScreeningService;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ARIREGISTER_SMOKE_TESTS", matches = "true")
class CompanyOnboardingDryRunSmokeTest {

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper objectMapper;
  @MockitoSpyBean private KybScreeningService kybScreeningService;
  @MockitoBean private PepAndSanctionCheckService pepAndSanctionCheckService;

  @BeforeEach
  void stubSanctionsToEmpty() {
    var empty = new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    given(pepAndSanctionCheckService.match(any(), any())).willReturn(empty);
    given(pepAndSanctionCheckService.matchCompany(any())).willReturn(empty);
  }

  @Test
  void runOnboardingForCompany() throws Exception {
    var registryCode = System.getenv("REGISTRY_CODE");
    var personalCode = System.getenv("PERSONAL_CODE");
    if (registryCode == null || personalCode == null) {
      throw new IllegalStateException(
          "Set REGISTRY_CODE and PERSONAL_CODE env vars for this smoke test");
    }

    var capturedChecks = new AtomicReference<List<KybCheck>>();
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<KybCheck> result = (List<KybCheck>) invocation.callRealMethod();
              capturedChecks.set(result);
              return result;
            })
        .when(kybScreeningService)
        .validate(any());

    var response =
        mvc.perform(
                get("/v1/kyb/surveys/initial-validation")
                    .param("registry-code", registryCode)
                    .with(authentication(authFor(personalCode))))
            .andReturn()
            .getResponse();

    System.out.println();
    System.out.println("=== Dry-run KYB onboarding (via controller) ===");
    System.out.printf("registryCode=%s, personalCode=%s%n", registryCode, personalCode);
    System.out.printf("HTTP %d%n", response.getStatus());
    System.out.println("Response body:");
    System.out.println(response.getContentAsString());

    var checks = capturedChecks.get();
    if (checks != null) {
      System.out.println("Raw checks (PEP/sanctions stubbed to empty):");
      checks.forEach(
          c ->
              System.out.printf(
                  "  [%s] %s — metadata=%s%n",
                  c.success() ? "PASS" : "FAIL", c.type(), c.metadata()));
      var failed = checks.stream().filter(c -> !c.success()).toList();
      System.out.printf(
          "Outcome: %s (%d failed of %d)%n",
          failed.isEmpty() ? "PASS" : "FAIL", failed.size(), checks.size());
    } else {
      System.out.println(
          "(screening did not run — likely blocked at board-member check or earlier)");
    }
  }

  private static UsernamePasswordAuthenticationToken authFor(String personalCode) {
    return new UsernamePasswordAuthenticationToken(
        AuthenticatedPerson.builder()
            .personalCode(personalCode)
            .firstName("Smoke")
            .lastName("Test")
            .userId(1L)
            .role(new Role(PERSON, personalCode, "Smoke Test"))
            .build(),
        null,
        List.of(new SimpleGrantedAuthority(USER)));
  }
}
