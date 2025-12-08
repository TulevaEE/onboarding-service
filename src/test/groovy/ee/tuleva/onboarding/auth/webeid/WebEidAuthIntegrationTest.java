package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.command.AuthenticationType.ID_CARD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebEidAuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void authenticateWithIdCardReturnsChallenge() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("type", ID_CARD.toString()));

    mockMvc
        .perform(post("/authenticate").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode").isNotEmpty())
        .andExpect(jsonPath("$.challengeCode").isString());
  }

  @Test
  void tokenWithInvalidAuthTokenReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/oauth/token")
                .param("grant_type", "ID_CARD")
                .param("authenticationHash", "invalid-json-token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tokenWithNullAuthTokenReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/oauth/token").param("grant_type", "ID_CARD"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tokenWithInvalidWebEidTokenReturnsBadRequest() throws Exception {
    String invalidAuthToken =
        """
        {"format":"web-eid:1","unverifiedCertificate":"invalid","algorithm":"ES384","signature":"sig"}
        """;

    mockMvc
        .perform(
            post("/oauth/token")
                .param("grant_type", "ID_CARD")
                .param("authenticationHash", invalidAuthToken))
        .andExpect(status().isBadRequest());
  }
}
