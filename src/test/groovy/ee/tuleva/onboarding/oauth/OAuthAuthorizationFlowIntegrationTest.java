package ee.tuleva.onboarding.oauth;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "oauth.clients.portfellow.name=Portfellow",
      "oauth.clients.portfellow.redirect-uris=https://app.example/callback",
      "oauth.clients.portfellow.scopes=balances:read,transactions:read",
      "oauth.clients.portfellow.public-keys=classpath:oauth/test-client-public-key.pem,classpath:oauth/test-client-rotated-public-key.pem",
      "oauth.clients.other-client.name=Other",
      "oauth.clients.other-client.redirect-uris=https://other.example/callback",
      "oauth.clients.other-client.scopes=balances:read",
      "oauth.clients.other-client.public-keys=classpath:oauth/other-client-public-key.pem",
    })
@Transactional
class OAuthAuthorizationFlowIntegrationTest {

  private static final String ISSUER = "http://localhost:9000";
  private static final String CONNECT_API_AUDIENCE = ISSUER + "/connect/v1";
  private static final String PERSONAL_CODE = "38812121215";
  private static final String PORTFELLOW = "portfellow";
  private static final String PORTFELLOW_CALLBACK = "https://app.example/callback";
  private static final String OTHER_CLIENT = "other-client";
  private static final String OTHER_CALLBACK = "https://other.example/callback";
  private static final String CLIENT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  private static final Path KILL_SWITCH = Path.of("docs/runbooks/oauth-kill-switch-portfellow.sql");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenUtil jwtTokenUtil;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcClient jdbcClient;

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aPersonWhoLogsInAfterTheRequestConnectsTheClientAndTheClientGetsTokensForThatPerson()
      throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", pkce);

    mockMvc
        .perform(get("/v1/connect/{id}", connectRequest.id()).cookie(connectRequest.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientName").value("Portfellow"))
        .andExpect(jsonPath("$.scopes[0]").value("balances:read"))
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());

    var redirect = approve(connectRequest, smartIdLoginNow());
    assertThat(redirect.getPath()).isEqualTo("/callback");
    assertThat(redirect.getQueryParams().getFirst("state")).isEqualTo("client-state");

    var tokens =
        exchangeCode(
            PORTFELLOW, PORTFELLOW_CALLBACK, redirect.getQueryParams().getFirst("code"), pkce);

    var claims = claimsOf(tokens.get("access_token").asString());
    assertThat(claims.get("iss").asString()).isEqualTo(ISSUER);
    assertThat(claims.get("aud").asString()).isEqualTo(CONNECT_API_AUDIENCE);
    assertThat(UUID.fromString(claims.get("sub").asString())).isNotNull();
    assertThat(claims.get("sub").asString()).isNotEqualTo(PERSONAL_CODE);
    assertThat(UUID.fromString(claims.get("grant_id").asString())).isNotNull();
    assertThat(claims.get("scope").get(0).asString()).isEqualTo("balances:read");
    assertThat(tokens.get("refresh_token").asString()).isNotBlank();
  }

  @Test
  void aClientSeesTheSamePersonUnderTheSameIdAcrossConnectionsAndAnotherClientUnderAnother()
      throws Exception {
    var first = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var second = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var other = connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");

    var firstSubject = claimsOf(first.get("access_token").asString()).get("sub").asString();
    var secondSubject = claimsOf(second.get("access_token").asString()).get("sub").asString();
    var otherSubject = claimsOf(other.get("access_token").asString()).get("sub").asString();

    assertThat(secondSubject).isEqualTo(firstSubject);
    assertThat(otherSubject).isNotEqualTo(firstSubject);
  }

  @Test
  void aRefreshRotatesTheRefreshToken() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    var refreshed = refresh(PORTFELLOW, tokens.get("refresh_token").asString());

    assertThat(refreshed.get("refresh_token").asString())
        .isNotEqualTo(tokens.get("refresh_token").asString());
    assertThat(refreshed.get("access_token").asString()).isNotBlank();
  }

  @Test
  void aSpentRefreshTokenIsRefusedAndEndsTheGrant() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var spent = tokens.get("refresh_token").asString();
    var current = refresh(PORTFELLOW, spent).get("refresh_token").asString();

    refreshExpectingInvalidGrant(PORTFELLOW, spent);

    refreshExpectingInvalidGrant(PORTFELLOW, current);
  }

  @Test
  void aGrantEndsAfterThirtyDaysWithoutARefresh() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    ClockHolder.setClock(Clock.fixed(Instant.now().plus(31, DAYS), ZoneOffset.UTC));

    refreshExpectingInvalidGrant(PORTFELLOW, tokens.get("refresh_token").asString());
  }

  @Test
  void aGrantEndsAfterOneHundredEightyDaysEvenWhenRefreshedThroughout() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var refreshToken = tokens.get("refresh_token").asString();
    var grantedAt = Instant.now();

    for (int day = 25; day < 180; day += 25) {
      ClockHolder.setClock(Clock.fixed(grantedAt.plus(day, DAYS), ZoneOffset.UTC));
      refreshToken = refresh(PORTFELLOW, refreshToken).get("refresh_token").asString();
    }
    ClockHolder.setClock(Clock.fixed(grantedAt.plus(181, DAYS), ZoneOffset.UTC));

    refreshExpectingInvalidGrant(PORTFELLOW, refreshToken);
  }

  @Test
  void aPartnerHandoverLoginCannotApprove() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    mockMvc
        .perform(
            approveRequest(connectRequest, bearer(loggedIn("PARTNER", Instant.now(), ownRole()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("connect.login.required"));
  }

  @Test
  void aLoginFromBeforeTheRequestCannotApprove() throws Exception {
    var loginBeforeRequest = Instant.now().minus(1, MINUTES);
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    mockMvc
        .perform(
            approveRequest(
                connectRequest, bearer(loggedIn("SMART_ID", loginBeforeRequest, ownRole()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("connect.login.required"));
  }

  @Test
  void aPersonActingForSomeoneElseCannotApprove() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var actingForCompany = new Role(LEGAL_ENTITY, "12345678", "Company OÜ");

    mockMvc
        .perform(
            approveRequest(
                connectRequest, bearer(loggedIn("SMART_ID", Instant.now(), actingForCompany))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("connect.login.required"));
  }

  @Test
  void aRequestCannotBeApprovedFromABrowserThatDidNotStartIt() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var anotherBrowser = new ConnectRequest(connectRequest.id(), new Cookie("oauth_connect", "x"));

    mockMvc
        .perform(get("/v1/connect/{id}", connectRequest.id()).cookie(anotherBrowser.cookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("connect.request.not.found"));
    mockMvc
        .perform(approveRequest(anotherBrowser, bearer(smartIdLoginNow())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("connect.request.not.found"));
  }

  @Test
  void aRequestCanBeApprovedOnlyOnce() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    approve(connectRequest, smartIdLoginNow());

    mockMvc
        .perform(approveRequest(connectRequest, bearer(smartIdLoginNow())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("connect.request.not.found"));
  }

  @Test
  void aRequestExpiresAfterTenMinutes() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    var elevenMinutesLater = Instant.now().plus(11, MINUTES);
    ClockHolder.setClock(Clock.fixed(elevenMinutesLater, ZoneOffset.UTC));

    mockMvc
        .perform(
            approveRequest(
                connectRequest, bearer(loggedIn("SMART_ID", elevenMinutesLater, ownRole()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void denyingSendsThePersonBackToTheClientWithAccessDenied() throws Exception {
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    var result =
        mockMvc
            .perform(
                post("/v1/connect/{id}/deny", connectRequest.id()).cookie(connectRequest.cookie()))
            .andExpect(status().isOk())
            .andReturn();

    var redirect = redirectUriOf(result);
    assertThat(redirect.getHost()).isEqualTo("app.example");
    assertThat(redirect.getQueryParams().getFirst("error")).isEqualTo("access_denied");
    assertThat(redirect.getQueryParams().getFirst("state")).isEqualTo("client-state");
    assertThat(redirect.getQueryParams().getFirst("code")).isNull();
  }

  @Test
  void anUnknownRedirectUriGetsAnErrorAndNeverARedirect() throws Exception {
    mockMvc
        .perform(
            get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", PORTFELLOW)
                .queryParam("redirect_uri", "https://attacker.example/callback")
                .queryParam("scope", "balances:read")
                .queryParam("state", "client-state")
                .queryParam("code_challenge", Pkce.generate().challenge())
                .queryParam("code_challenge_method", "S256"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aRequestWithoutPkceIsRefused() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", PORTFELLOW)
                    .queryParam("redirect_uri", PORTFELLOW_CALLBACK)
                    .queryParam("scope", "balances:read")
                    .queryParam("state", "client-state"))
            .andReturn();

    assertThat(result.getResponse().getHeader(LOCATION))
        .startsWith(PORTFELLOW_CALLBACK)
        .contains("error=invalid_request");
  }

  @Test
  void aRequestWithoutAScopeIsSentBackWithInvalidScope() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", PORTFELLOW)
                    .queryParam("redirect_uri", PORTFELLOW_CALLBACK)
                    .queryParam("state", "client-state")
                    .queryParam("code_challenge", Pkce.generate().challenge())
                    .queryParam("code_challenge_method", "S256"))
            .andExpect(status().isFound())
            .andReturn();

    assertThat(result.getResponse().getHeader(LOCATION))
        .startsWith(PORTFELLOW_CALLBACK)
        .contains("error=invalid_scope");
  }

  @Test
  void aRequestWithoutARedirectUriGetsAnErrorAndNeverARedirect() throws Exception {
    mockMvc
        .perform(
            get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", PORTFELLOW)
                .queryParam("scope", "balances:read")
                .queryParam("state", "client-state")
                .queryParam("code_challenge", Pkce.generate().challenge())
                .queryParam("code_challenge_method", "S256"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aWebAppLoginOnTheAuthorizationEndpointAuthenticatesNothing() throws Exception {
    var result =
        mockMvc
            .perform(
                authorizationRequest(
                        PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", Pkce.generate())
                    .header(AUTHORIZATION, "Bearer " + bearer(smartIdLoginNow())))
            .andExpect(status().isFound())
            .andReturn();

    assertThat(result.getResponse().getHeader(LOCATION))
        .startsWith("http://localhost:3000/connect/");
  }

  @Test
  void aClientAssertionSignedWithAnUnregisteredKeyIsRefused() throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", pkce);
    var code = approve(connectRequest, smartIdLoginNow()).getQueryParams().getFirst("code");

    mockMvc
        .perform(
            post("/oauth2/token")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", PORTFELLOW_CALLBACK)
                .param("code_verifier", pkce.verifier())
                .param("client_id", PORTFELLOW)
                .param("client_assertion_type", CLIENT_ASSERTION_TYPE)
                .param(
                    "client_assertion",
                    clientAssertion(PORTFELLOW, privateKey("oauth/other-client-private-key.pem"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aClientAssertionSignedWithTheSecondPinnedKeyIsAccepted() throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", pkce);
    var code = approve(connectRequest, smartIdLoginNow()).getQueryParams().getFirst("code");

    mockMvc
        .perform(
            tokenRequest(
                    PORTFELLOW,
                    privateKey("oauth/test-client-rotated-private-key.pem"),
                    "authorization_code")
                .param("code", code)
                .param("redirect_uri", PORTFELLOW_CALLBACK)
                .param("code_verifier", pkce.verifier()))
        .andExpect(status().isOk());
  }

  @Test
  void theKillSwitchEndsEveryGrantOfTheClientAndRefusesItUntilLifted() throws Exception {
    var portfellowTokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var otherTokens = connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");

    new ResourceDatabasePopulator(new FileSystemResource(KILL_SWITCH)).execute(dataSource);

    refreshExpectingInvalidClient(PORTFELLOW, portfellowTokens.get("refresh_token").asString());
    mockMvc
        .perform(
            authorizationRequest(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", Pkce.generate()))
        .andExpect(status().isBadRequest());
    assertThat(
            refresh(OTHER_CLIENT, otherTokens.get("refresh_token").asString()).get("access_token"))
        .isNotNull();
  }

  @Test
  void theDatabaseHoldsOnlyHashesOfTheTokens() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var accessToken = tokens.get("access_token").asString();
    var refreshToken = tokens.get("refresh_token").asString();

    var storedTokens =
        jdbcClient
            .sql("SELECT access_token_hash, refresh_token_hash FROM oauth_grant")
            .query((resultSet, rowNum) -> List.of(resultSet.getString(1), resultSet.getString(2)))
            .single();

    assertThat(storedTokens).containsExactly(sha256(accessToken), sha256(refreshToken));
  }

  @Test
  void revokingTheRefreshTokenEndsTheGrant() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var refreshToken = tokens.get("refresh_token").asString();

    mockMvc
        .perform(
            post("/oauth2/revoke")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("token", refreshToken)
                .param("token_type_hint", "refresh_token")
                .param("client_id", PORTFELLOW)
                .param("client_assertion_type", CLIENT_ASSERTION_TYPE)
                .param(
                    "client_assertion", clientAssertion(PORTFELLOW, clientPrivateKey(PORTFELLOW))))
        .andExpect(status().isOk());

    refreshExpectingInvalidGrant(PORTFELLOW, refreshToken);
  }

  @Test
  void theMetadataAdvertisesOnlyWhatClientsMayUse() throws Exception {
    mockMvc
        .perform(get("/.well-known/oauth-authorization-server"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.grant_types_supported.length()").value(2))
        .andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
        .andExpect(jsonPath("$.grant_types_supported[1]").value("refresh_token"))
        .andExpect(jsonPath("$.token_endpoint_auth_methods_supported.length()").value(1))
        .andExpect(jsonPath("$.token_endpoint_auth_methods_supported[0]").value("private_key_jwt"))
        .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
        .andExpect(jsonPath("$.introspection_endpoint").doesNotExist());
  }

  @Test
  void tokenIntrospectionIsNotOffered() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    mockMvc
        .perform(
            post("/oauth2/introspect")
                .contentType(APPLICATION_FORM_URLENCODED)
                .param("token", tokens.get("access_token").asString())
                .param("client_id", PORTFELLOW)
                .param("client_assertion_type", CLIENT_ASSERTION_TYPE)
                .param(
                    "client_assertion", clientAssertion(PORTFELLOW, clientPrivateKey(PORTFELLOW))))
        .andExpect(status().isForbidden());
  }

  private JsonNode connect(String clientId, String callback, String scope) throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(clientId, callback, scope, pkce);
    var code = approve(connectRequest, smartIdLoginNow()).getQueryParams().getFirst("code");
    return exchangeCode(clientId, callback, code, pkce);
  }

  private ConnectRequest startAuthorization(String clientId, String callback, String scope)
      throws Exception {
    return startAuthorization(clientId, callback, scope, Pkce.generate());
  }

  private ConnectRequest startAuthorization(
      String clientId, String callback, String scope, Pkce pkce) throws Exception {
    var result =
        mockMvc
            .perform(authorizationRequest(clientId, callback, scope, pkce))
            .andExpect(status().isFound())
            .andReturn();

    var location = result.getResponse().getHeader(LOCATION);
    assertThat(location).startsWith("http://localhost:3000/connect/");
    var cookie = result.getResponse().getCookie("oauth_connect");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getPath()).isEqualTo("/v1/connect");
    return new ConnectRequest(location.substring(location.lastIndexOf('/') + 1), cookie);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      authorizationRequest(String clientId, String callback, String scope, Pkce pkce) {
    return get("/oauth2/authorize")
        .queryParam("response_type", "code")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", callback)
        .queryParam("scope", scope)
        .queryParam("state", "client-state")
        .queryParam("code_challenge", pkce.challenge())
        .queryParam("code_challenge_method", "S256");
  }

  private org.springframework.web.util.UriComponents approve(
      ConnectRequest connectRequest, AuthenticatedPerson person) throws Exception {
    var result =
        mockMvc
            .perform(approveRequest(connectRequest, bearer(person)))
            .andExpect(status().isOk())
            .andReturn();
    return redirectUriOf(result);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder approveRequest(
      ConnectRequest connectRequest, String bearer) {
    return post("/v1/connect/{id}/approve", connectRequest.id())
        .cookie(connectRequest.cookie())
        .header(AUTHORIZATION, "Bearer " + bearer);
  }

  private org.springframework.web.util.UriComponents redirectUriOf(MvcResult result)
      throws Exception {
    var redirectUri =
        jsonMapper
            .readTree(result.getResponse().getContentAsString())
            .get("redirectUri")
            .asString();
    return UriComponentsBuilder.fromUriString(redirectUri).build();
  }

  private JsonNode exchangeCode(String clientId, String callback, String code, Pkce pkce)
      throws Exception {
    var result =
        mockMvc
            .perform(
                tokenRequest(clientId, clientPrivateKey(clientId), "authorization_code")
                    .param("code", code)
                    .param("redirect_uri", callback)
                    .param("code_verifier", pkce.verifier()))
            .andExpect(status().isOk())
            .andReturn();
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode refresh(String clientId, String refreshToken) throws Exception {
    var result =
        mockMvc
            .perform(
                tokenRequest(clientId, clientPrivateKey(clientId), "refresh_token")
                    .param("refresh_token", refreshToken))
            .andExpect(status().isOk())
            .andReturn();
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  private void refreshExpectingInvalidGrant(String clientId, String refreshToken) throws Exception {
    mockMvc
        .perform(
            tokenRequest(clientId, clientPrivateKey(clientId), "refresh_token")
                .param("refresh_token", refreshToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_grant"));
  }

  private void refreshExpectingInvalidClient(String clientId, String refreshToken)
      throws Exception {
    mockMvc
        .perform(
            tokenRequest(clientId, clientPrivateKey(clientId), "refresh_token")
                .param("refresh_token", refreshToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder tokenRequest(
      String clientId, PrivateKey key, String grantType) throws Exception {
    return post("/oauth2/token")
        .contentType(APPLICATION_FORM_URLENCODED)
        .param("grant_type", grantType)
        .param("client_id", clientId)
        .param("client_assertion_type", CLIENT_ASSERTION_TYPE)
        .param("client_assertion", clientAssertion(clientId, key));
  }

  private static String clientAssertion(String clientId, PrivateKey key) throws Exception {
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(clientId)
            .subject(clientId)
            .audience(ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static PrivateKey clientPrivateKey(String clientId) throws Exception {
    return privateKey(
        clientId.equals(PORTFELLOW)
            ? "oauth/test-client-private-key.pem"
            : "oauth/other-client-private-key.pem");
  }

  private static PrivateKey privateKey(String resource) throws Exception {
    var pem = new String(new ClassPathResource(resource).getInputStream().readAllBytes(), UTF_8);
    var base64 =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
  }

  private AuthenticatedPerson smartIdLoginNow() {
    return loggedIn("SMART_ID", Instant.now(ClockHolder.clock()), ownRole());
  }

  private static Role ownRole() {
    return new Role(PERSON, PERSONAL_CODE, "Jordan Valdma");
  }

  private static AuthenticatedPerson loggedIn(String grantType, Instant authTime, Role role) {
    return AuthenticatedPerson.builder()
        .personalCode(PERSONAL_CODE)
        .firstName("Jordan")
        .lastName("Valdma")
        .attributes(Map.of("grantType", grantType, "authTime", authTime.toString()))
        .role(role)
        .build();
  }

  private String bearer(AuthenticatedPerson person) {
    return jwtTokenUtil.generateAccessToken(person, List.of(new SimpleGrantedAuthority(USER)));
  }

  private JsonNode claimsOf(String jwt) {
    var payload = jwt.split("\\.")[1];
    return jsonMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), UTF_8));
  }

  private static String sha256(String token) throws Exception {
    return java.util.HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(US_ASCII)));
  }

  private record ConnectRequest(String id, Cookie cookie) {}

  private record Pkce(String verifier, String challenge) {
    static Pkce generate() throws Exception {
      var bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      var verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(US_ASCII));
      return new Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
    }
  }
}
