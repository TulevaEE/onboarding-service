package ee.tuleva.onboarding.auth.webeid;

import static ee.tuleva.onboarding.auth.command.AuthenticationType.ID_CARD;
import static ee.tuleva.onboarding.auth.webeid.WebEidCertificateFixture.certificateWithoutPolicies;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.webeid.security.authtoken.WebEidAuthToken;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class WebEidAuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;

  @Test
  void authenticateWithIdCardReturnsChallenge() throws Exception {
    var requestBody = objectMapper.writeValueAsString(Map.of("type", ID_CARD.toString()));

    mockMvc
        .perform(post("/authenticate").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode").isNotEmpty())
        .andExpect(jsonPath("$.challengeCode").isString())
        // Spring Session JDBC must be wired so HttpSession state survives across ECS tasks.
        // Without the starter, Tomcat falls back to in-memory sessions + JSESSIONID cookie.
        .andExpect(cookie().exists("SESSION"));
  }

  @Test
  void invalidAuthTokenReturnsBadRequest() throws Exception {
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

  @Test
  void tokenWithCertificateWithoutPoliciesReturnsBadRequest() throws Exception {
    var challenge =
        mockMvc
            .perform(
                post("/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("type", ID_CARD.toString()))))
            .andExpect(status().isOk())
            .andReturn();
    var certificate = certificateWithoutPolicies("MARI-LIIS", "MÄNNIK", "38888888888");
    var authToken = new WebEidAuthToken();
    authToken.setFormat("web-eid:1.0");
    authToken.setUnverifiedCertificate(
        Base64.getEncoder().encodeToString(certificate.getEncoded()));
    authToken.setAlgorithm("ES384");
    authToken.setSignature("signature");

    mockMvc
        .perform(
            post("/oauth/token")
                .cookie(challenge.getResponse().getCookie("SESSION"))
                .param("grant_type", "ID_CARD")
                .param("authenticationHash", objectMapper.writeValueAsString(authToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("ID_CARD_AUTH_FAILED"));
  }
}
