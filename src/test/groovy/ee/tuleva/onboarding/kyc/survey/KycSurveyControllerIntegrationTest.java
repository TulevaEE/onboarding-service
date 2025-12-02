package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KycSurveyControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private KycSurveyRepository kycSurveyRepository;
  @Autowired private UserRepository userRepository;

  private User user;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    user = userRepository.save(sampleUserNonMember().id(null).build());

    var authPerson = authenticatedPersonFromUser(user).build();

    authentication =
        new UsernamePasswordAuthenticationToken(
            authPerson, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  @DisplayName("POST /v1/kyc/surveys persists survey to database")
  void post_persistsSurveyToDatabase() throws Exception {
    String requestBody =
        """
        {
          "answers": [
            {
              "type": "CITIZENSHIP",
              "value": {
                "type": "COUNTRIES",
                "value": ["EE", "FI"]
              }
            },
            {
              "type": "ADDRESS",
              "value": {
                "type": "ADDRESS",
                "value": {
                  "street": "123 Main St",
                  "city": "Tallinn",
                  "state": "Harjumaa",
                  "postalCode": "10115",
                  "countryCode": "EE"
                }
              }
            },
            {
              "type": "EMAIL",
              "value": {
                "type": "TEXT",
                "value": "test@example.com"
              }
            },
            {
              "type": "PEP_SELF_DECLARATION",
              "value": {
                "type": "OPTION",
                "value": "IS_NOT_PEP"
              }
            },
            {
              "type": "INVESTMENT_GOALS",
              "value": {
                "type": "OPTION",
                "value": "LONG_TERM"
              }
            },
            {
              "type": "INVESTABLE_ASSETS",
              "value": {
                "type": "OPTION",
                "value": "LESS_THAN_20K"
              }
            },
            {
              "type": "SOURCE_OF_INCOME",
              "value": {
                "type": "MULTI_OPTION",
                "value": ["SALARY", "SAVINGS"]
              }
            },
            {
              "type": "TERMS",
              "value": {
                "type": "OPTION",
                "value": "ACCEPTED"
              }
            }
          ]
        }
        """;

    mockMvc
        .perform(
            post("/v1/kyc/surveys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isCreated());

    var surveys = kycSurveyRepository.findAll();
    assertThat(surveys).hasSize(1);

    var saved = surveys.getFirst();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getSurvey().answers()).hasSize(8);
    assertThat(saved.getCreatedTime()).isNotNull();
  }
}
