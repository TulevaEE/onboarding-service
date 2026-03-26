package ee.tuleva.onboarding.auth.role;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.company.CompanyFixture.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoleController.class)
@AutoConfigureMockMvc
class RoleControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RoleSwitchService roleSwitchService;

  private static UsernamePasswordAuthenticationToken userAuth() {
    var person =
        AuthenticatedPerson.builder()
            .personalCode("38501010002")
            .firstName("Jordan")
            .lastName("Valdma")
            .userId(1L)
            .build();
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void switchRoleDelegatesToService() throws Exception {
    when(roleSwitchService.switchRole(any(AuthenticatedPerson.class), any(SwitchRoleCommand.class)))
        .thenReturn(new AuthenticationTokens("access-token", "refresh-token"));

    mockMvc
        .perform(
            post("/v1/me/role")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"LEGAL_ENTITY","code":"%s"}"""
                        .formatted(SAMPLE_REGISTRY_CODE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("access-token"))
        .andExpect(jsonPath("$.refresh_token").value("refresh-token"));
  }

  @Test
  void switchRoleReturns404WhenCompanyNotFound() throws Exception {
    when(roleSwitchService.switchRole(any(AuthenticatedPerson.class), any(SwitchRoleCommand.class)))
        .thenThrow(new CompanyNotFoundException("99999999"));

    mockMvc
        .perform(
            post("/v1/me/role")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"LEGAL_ENTITY","code":"99999999"}"""))
        .andExpect(status().isNotFound());
  }

  @Test
  void switchRoleReturns403WhenAccessDenied() throws Exception {
    when(roleSwitchService.switchRole(any(AuthenticatedPerson.class), any(SwitchRoleCommand.class)))
        .thenThrow(new RoleSwitchAccessDeniedException("38501010002", "12345678"));

    mockMvc
        .perform(
            post("/v1/me/role")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"PERSON","code":"99999999999"}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  void getRolesDelegatesToService() throws Exception {
    when(roleSwitchService.getRoles(any(AuthenticatedPerson.class)))
        .thenReturn(
            List.of(
                new Role(RoleType.PERSON, "38501010002", "Jordan Valdma"),
                new Role(RoleType.LEGAL_ENTITY, SAMPLE_REGISTRY_CODE, SAMPLE_COMPANY_NAME)));

    mockMvc
        .perform(get("/v1/me/roles").with(authentication(userAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("PERSON"))
        .andExpect(jsonPath("$[0].code").value("38501010002"))
        .andExpect(jsonPath("$[0].name").value("Jordan Valdma"))
        .andExpect(jsonPath("$[1].type").value("LEGAL_ENTITY"))
        .andExpect(jsonPath("$[1].code").value(SAMPLE_REGISTRY_CODE))
        .andExpect(jsonPath("$[1].name").value(SAMPLE_COMPANY_NAME));
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc
        .perform(
            post("/v1/me/role")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"LEGAL_ENTITY","code":"12345678"}"""))
        .andExpect(status().isUnauthorized());
  }
}
