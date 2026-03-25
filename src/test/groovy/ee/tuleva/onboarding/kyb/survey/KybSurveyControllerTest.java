package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KybSurveyController.class)
@AutoConfigureMockMvc
@WithMockUser
class KybSurveyControllerTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String PERSONAL_CODE = "38501010002";

  @Autowired private MockMvc mvc;

  @MockitoBean private KybSurveyService kybSurveyService;

  @Test
  void initialValidation_returnsLegalEntityData() throws Exception {
    var data =
        new LegalEntityData(
            ValidatedField.valid("Test OÜ"),
            ValidatedField.valid(REGISTRY_CODE),
            ValidatedField.valid("OÜ"),
            ValidatedField.valid(LegalEntityStatus.REGISTERED),
            ValidatedField.valid("Tallinn"),
            ValidatedField.valid("Fondide valitsemine"),
            ValidatedField.valid("6630"),
            ValidatedField.valid(List.of()));
    when(kybSurveyService.initialValidation(REGISTRY_CODE, PERSONAL_CODE)).thenReturn(data);

    mvc.perform(get("/v1/kyb/surveys/initial-validation").with(authentication(legalEntityAuth())))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"name\":{\"value\":\"Test OÜ\",\"errors\":[]}}"))
        .andExpect(content().json("{\"status\":{\"value\":\"REGISTERED\",\"errors\":[]}}"))
        .andExpect(content().json("{\"naceCode\":{\"value\":\"6630\",\"errors\":[]}}"));
  }

  @Test
  void initialValidation_returns403WhenActingAsPerson() throws Exception {
    mvc.perform(get("/v1/kyb/surveys/initial-validation").with(authentication(personAuth())))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken legalEntityAuth() {
    return new UsernamePasswordAuthenticationToken(
        AuthenticatedPerson.builder()
            .personalCode(PERSONAL_CODE)
            .firstName("Jaan")
            .lastName("Tamm")
            .userId(1L)
            .role(new Role(LEGAL_ENTITY, REGISTRY_CODE, "Test OÜ"))
            .build(),
        null,
        List.of(new SimpleGrantedAuthority(USER)));
  }

  private UsernamePasswordAuthenticationToken personAuth() {
    return new UsernamePasswordAuthenticationToken(
        AuthenticatedPerson.builder()
            .personalCode(PERSONAL_CODE)
            .firstName("Jaan")
            .lastName("Tamm")
            .userId(1L)
            .role(new Role(PERSON, PERSONAL_CODE, "Jaan Tamm"))
            .build(),
        null,
        List.of(new SimpleGrantedAuthority(USER)));
  }
}
