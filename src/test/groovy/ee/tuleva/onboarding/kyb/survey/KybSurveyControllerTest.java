package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
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
            ValidatedField.valid(LocalDate.of(2020, 1, 15)),
            ValidatedField.valid(LegalEntityStatus.REGISTERED),
            ValidatedField.valid(
                new LegalEntityAddress(
                    "Pärnu mnt 123, 11313 Tallinn", "Pärnu mnt 123", "Tallinn", "11313", "EST")),
            ValidatedField.valid("Fondide valitsemine"),
            ValidatedField.valid("6630"),
            ValidatedField.valid(List.of()));
    when(kybSurveyService.initialValidation(REGISTRY_CODE, PERSONAL_CODE)).thenReturn(data);

    mvc.perform(
            get("/v1/kyb/surveys/initial-validation")
                .param("registry-code", REGISTRY_CODE)
                .with(authentication(personAuth())))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"name\":{\"value\":\"Test OÜ\",\"errors\":[]}}"))
        .andExpect(content().json("{\"status\":{\"value\":\"REGISTERED\",\"errors\":[]}}"))
        .andExpect(content().json("{\"naceCode\":{\"value\":\"6630\",\"errors\":[]}}"))
        .andExpect(
            content()
                .json(
                    "{\"address\":{\"value\":{\"fullAddress\":\"Pärnu mnt 123, 11313 Tallinn\","
                        + "\"street\":\"Pärnu mnt 123\",\"city\":\"Tallinn\","
                        + "\"postalCode\":\"11313\",\"countryCode\":\"EST\"},\"errors\":[]}}"));
  }

  @Test
  void initialValidation_returns403WhenNotBoardMember() throws Exception {
    when(kybSurveyService.initialValidation(REGISTRY_CODE, PERSONAL_CODE))
        .thenThrow(new NotBoardMemberException(REGISTRY_CODE, PERSONAL_CODE));

    mvc.perform(
            get("/v1/kyb/surveys/initial-validation")
                .param("registry-code", REGISTRY_CODE)
                .with(authentication(personAuth())))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"error\":\"NOT_BOARD_MEMBER\"}"));
  }

  @Test
  void submit_returns200() throws Exception {
    mvc.perform(
            post("/v1/kyb/surveys")
                .param("registry-code", REGISTRY_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SURVEY_JSON)
                .with(csrf())
                .with(authentication(personAuth())))
        .andExpect(status().isOk());
  }

  @Test
  void initialValidation_returns501OnUnexpectedError() throws Exception {
    when(kybSurveyService.initialValidation(REGISTRY_CODE, PERSONAL_CODE))
        .thenThrow(new RuntimeException("Ariregister timeout"));

    mvc.perform(
            get("/v1/kyb/surveys/initial-validation")
                .param("registry-code", REGISTRY_CODE)
                .with(authentication(personAuth())))
        .andExpect(status().isNotImplemented())
        .andExpect(content().json("{\"error\":\"UNEXPECTED_ERROR\"}"));
  }

  @Test
  void submit_returns501OnUnexpectedError() throws Exception {
    willThrow(new RuntimeException("Ariregister timeout"))
        .given(kybSurveyService)
        .submit(eq(1L), eq(PERSONAL_CODE), eq(REGISTRY_CODE), any(KybSurveyResponse.class));

    mvc.perform(
            post("/v1/kyb/surveys")
                .param("registry-code", REGISTRY_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SURVEY_JSON)
                .with(csrf())
                .with(authentication(personAuth())))
        .andExpect(status().isNotImplemented())
        .andExpect(content().json("{\"error\":\"UNEXPECTED_ERROR\"}"));
  }

  @Test
  void submit_returns403WhenNotBoardMember() throws Exception {
    willThrow(new NotBoardMemberException(REGISTRY_CODE, PERSONAL_CODE))
        .given(kybSurveyService)
        .submit(eq(1L), eq(PERSONAL_CODE), eq(REGISTRY_CODE), any(KybSurveyResponse.class));

    mvc.perform(
            post("/v1/kyb/surveys")
                .param("registry-code", REGISTRY_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SURVEY_JSON)
                .with(csrf())
                .with(authentication(personAuth())))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"error\":\"NOT_BOARD_MEMBER\"}"));
  }

  private static final String SURVEY_JSON =
      """
      {
        "answers": [
          { "type": "COMPANY_SOURCE_OF_INCOME", "value": [{ "type": "OPTION", "value": "ONLY_ACTIVE_IN_ESTONIA" }] }
        ]
      }
      """;

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
