package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.nudge.NudgeContext.SAVINGS_FUND_PAYMENT;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NudgeController.class)
@Import(SecurityConfiguration.class)
class NudgeControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private NudgeDecisionService nudgeDecisionService;
  @MockitoBean private UserService userService;

  // Needed by the imported SecurityConfiguration's JwtAuthorizationFilter bean.
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private static Authentication authenticated(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void returnsTheDecisionForTheAuthenticatedUserAndContext() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
    User user = sampleUser().build();
    given(userService.getByIdOrThrow(person.getUserId())).willReturn(user);
    given(
            nudgeDecisionService.decide(
                eq(user), eq(NudgeAccount.of(person.getRole())), eq(THIRD_PILLAR_PAYMENT)))
        .willReturn(
            NudgeDecision.secondPillarTransfer(
                new FeeComparison(new BigDecimal("0.65"), 130, 56, 74)));

    mvc.perform(
            get("/v1/me/nudge")
                .param("context", "THIRD_PILLAR_PAYMENT")
                .with(authentication(authenticated(person))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value("SECOND_PILLAR_TRANSFER"))
        .andExpect(jsonPath("$.tag").value("nudge_second_pillar"))
        .andExpect(jsonPath("$.feeComparison.currentFeePercent").value(0.65))
        .andExpect(jsonPath("$.feeComparison.currentFeeAmount").value(130))
        .andExpect(jsonPath("$.feeComparison.tulevaFeeAmount").value(56))
        .andExpect(jsonPath("$.feeComparison.savingsAmount").value(74))
        .andExpect(jsonPath("$.savingsFundFeePercent").doesNotExist());
  }

  @Test
  void passesTheActingCompanyRoleAsTheAccountToDecideFor() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonLegalEntity().build();
    User user = sampleUser().build();
    given(userService.getByIdOrThrow(person.getUserId())).willReturn(user);
    given(
            nudgeDecisionService.decide(
                eq(user), eq(NudgeAccount.of(person.getRole())), eq(SAVINGS_FUND_PAYMENT)))
        .willReturn(NudgeDecision.of(NudgeKey.ACCOUNT_RECURRING));

    mvc.perform(
            get("/v1/me/nudge")
                .param("context", "SAVINGS_FUND_PAYMENT")
                .with(authentication(authenticated(person))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json("{\"key\":\"ACCOUNT_RECURRING\",\"tag\":\"nudge_savings_fund_recurring\"}"));
  }

  @Test
  void rejectsAnUnknownContext() throws Exception {
    AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();

    mvc.perform(
            get("/v1/me/nudge")
                .param("context", "SOMETHING_ELSE")
                .with(authentication(authenticated(person))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/v1/me/nudge").param("context", "THIRD_PILLAR_PAYMENT"))
        .andExpect(status().isForbidden());
  }
}
