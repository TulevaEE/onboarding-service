package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.HIGH;
import static ee.tuleva.onboarding.kyc.KycCheck.RiskLevel.LOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.kyc.*;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@Import(TestKycCheckerConfiguration.class)
class KycSurveyControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private KycSurveyRepository kycSurveyRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private TestKycChecker testKycChecker;

  private User user;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    testKycChecker.reset();
    user = userRepository.save(sampleUserNonMember().id(null).personalCode("48805051231").build());

    var authPerson = authenticatedPersonFromUser(user).build();

    authentication =
        new UsernamePasswordAuthenticationToken(
            authPerson, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
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
              "value": [
                { "type": "OPTION", "value": "SALARY" },
                { "type": "OPTION", "value": "SAVINGS" }
              ]
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
        .andExpect(status().isOk());

    var surveys = kycSurveyRepository.findAll();
    assertThat(surveys).hasSize(1);

    var saved = surveys.getFirst();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getSurvey().answers()).hasSize(8);
    assertThat(saved.getCreatedTime()).isNotNull();

    var beforeKycEvents = applicationEvents.stream(BeforeKycCheckedEvent.class).toList();
    assertThat(beforeKycEvents).hasSize(1);

    var beforeKycEvent = beforeKycEvents.getFirst();
    assertThat(beforeKycEvent.person().getPersonalCode()).isEqualTo(user.getPersonalCode());
    assertThat(beforeKycEvent.country().getCountryCode()).isEqualTo("EE");

    var events = applicationEvents.stream(KycCheckPerformedEvent.class).toList();
    assertThat(events).hasSize(1);

    var event = events.getFirst();
    assertThat(event.getPersonalCode()).isEqualTo(user.getPersonalCode());
    assertThat(event.getKycCheck()).isEqualTo(new KycCheck(LOW, Map.of()));
  }

  @Test
  void post_publishesHighRiskEvent_whenCheckerReturnsHighRisk() throws Exception {
    testKycChecker.givenKycCheck(user.getId(), new KycCheck(HIGH, Map.of("score", 100)));

    String requestBody =
        """
        {
          "answers": [
            {
              "type": "CITIZENSHIP",
              "value": {
                "type": "COUNTRIES",
                "value": ["EE"]
              }
            },
            {
              "type": "ADDRESS",
              "value": {
                "type": "ADDRESS",
                "value": {
                  "street": "123 Main St",
                  "city": "Tallinn",
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
              "value": [
                { "type": "OPTION", "value": "SALARY" }
              ]
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
        .andExpect(status().isOk());

    var events = applicationEvents.stream(KycCheckPerformedEvent.class).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().getKycCheck()).isEqualTo(new KycCheck(HIGH, Map.of("score", 100)));
  }
}
