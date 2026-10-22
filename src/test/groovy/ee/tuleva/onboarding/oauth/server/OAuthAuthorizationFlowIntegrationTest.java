package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.epis.CashFlow.Type.TRANSFER_IN;
import static ee.tuleva.onboarding.fund.Fund.FundStatus.ACTIVE;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ee.tuleva.onboarding.account.FundBalance;
import ee.tuleva.onboarding.account.SavingsFundStatementService;
import ee.tuleva.onboarding.account.transaction.Transaction;
import ee.tuleva.onboarding.auth.jwt.JwtAuthorizationFilter;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.manager.FundManager;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundTransactionService;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;
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
  private static final String API_AUDIENCE = ISSUER + "/v1";
  private static final String PERSONAL_CODE = "38812121215";
  private static final String ANOTHER_PERSONAL_CODE = "30303039816";
  private static final String PORTFELLOW = "portfellow";
  private static final String PORTFELLOW_CALLBACK = "https://app.example/callback";
  private static final String OTHER_CLIENT = "other-client";
  private static final String OTHER_CALLBACK = "https://other.example/callback";
  private static final String CLIENT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  private static final String BROWSER_ADDRESS = "203.0.113.7";
  private static final String BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)";
  private static final Path KILL_SWITCH = Path.of("docs/runbooks/oauth-kill-switch-portfellow.sql");
  private static final String SYNTHETIC_IBAN = "EE001010220000000000";
  private static final String APP_SERVER_ADDRESS = "198.51.100.20";

  private static final String PUBLISHED_SAVINGS_FUND_STATEMENT =
      """
      {
        "fund": {
          "fundManager": { "name": "Tuleva" },
          "isin": "EE0000000000",
          "name": "Example Savings Fund",
          "managementFeeRate": 0.0016,
          "pillar": null,
          "ongoingChargesFigure": 0.0029,
          "status": "ACTIVE",
          "inceptionDate": "2025-09-18"
        },
        "value": 1234.56,
        "unavailableValue": 0.00,
        "currency": "EUR",
        "activeContributions": false,
        "contributions": 1000.00,
        "subtractions": -100.00,
        "profit": 334.56,
        "units": 100.12345
      }
      """;

  private static final String PUBLISHED_TRANSACTIONS =
      """
      [
        {
          "id": "7d1a9f3e-2c4b-4e8a-9b6d-0f5e3a2c1b47",
          "amount": 400.00,
          "currency": "EUR",
          "time": "2026-10-14T09:00:00Z",
          "navDate": "2026-10-14",
          "priceCalculationDate": null,
          "settledTime": "2026-10-14T09:00:00Z",
          "applicationTime": null,
          "counterpartyIban": null,
          "isin": "EE0000000000",
          "type": "TRANSFER_IN",
          "units": 32.49391,
          "nav": null,
          "acquisitionCost": 350.00
        },
        {
          "id": "2b8e4d1c-6f3a-4a9b-8c7d-5e1f2a3b4c5d",
          "amount": -123.10,
          "currency": "EUR",
          "time": "2026-10-07T12:00:00Z",
          "navDate": "2026-10-06",
          "priceCalculationDate": "2026-10-07",
          "settledTime": "2026-10-09T08:00:00Z",
          "applicationTime": "2026-10-05T18:20:00Z",
          "counterpartyIban": "EE001010220000000000",
          "isin": "EE0000000000",
          "type": "SUBTRACTION",
          "units": 10.00000,
          "nav": 12.3100
        },
        {
          "id": "9c3f5a7b-1d2e-4f6a-8b9c-0d1e2f3a4b5c",
          "amount": 100.00,
          "currency": "EUR",
          "time": "2026-10-02T14:00:00Z",
          "navDate": "2026-10-01",
          "priceCalculationDate": "2026-10-02",
          "settledTime": "2026-10-02T14:00:00Z",
          "applicationTime": "2026-10-01T11:30:00Z",
          "counterpartyIban": "EE001010220000000000",
          "isin": "EE0000000000",
          "type": "CONTRIBUTION_CASH",
          "units": 8.13008,
          "nav": 12.3000
        }
      ]
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenUtil jwtTokenUtil;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private EventLogRepository eventLogRepository;
  @Autowired private FilterChainProxy filterChainProxy;
  @Autowired private SavingsFundLedger savingsFundLedger;
  @Autowired private SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  @Autowired
  @Qualifier("episRestTemplate")
  private RestTemplate episRestTemplate;

  @Autowired
  @Qualifier("episLongRequestRestTemplate")
  private RestTemplate episLongRequestRestTemplate;

  @MockitoSpyBean private SavingsFundStatementService savingsFundStatementService;
  @MockitoSpyBean private SavingsFundTransactionService savingsFundTransactionService;

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
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());

    var redirect = approve(connectRequest, smartIdLoginNow());
    assertThat(redirect.getPath()).isEqualTo("/callback");
    assertThat(redirect.getQueryParams().getFirst("state")).isEqualTo("client-state");

    var tokens =
        exchangeCode(
            PORTFELLOW, PORTFELLOW_CALLBACK, redirect.getQueryParams().getFirst("code"), pkce);

    var claims = claimsOf(tokens.get("access_token").asString());
    assertThat(headerOf(tokens.get("access_token").asString()).get("alg").asString())
        .isEqualTo("ES384");
    assertThat(headerOf(tokens.get("access_token").asString()).get("typ").asString())
        .isEqualTo("at+jwt");
    assertThat(claims.get("iss").asString()).isEqualTo(ISSUER);
    assertThat(claims.get("aud").asString()).isEqualTo(API_AUDIENCE);
    assertThat(UUID.fromString(claims.get("sub").asString())).isNotNull();
    assertThat(claims.get("sub").asString()).isNotEqualTo(PERSONAL_CODE);
    assertThat(UUID.fromString(claims.get("grant_id").asString())).isNotNull();
    assertThat(claims.get("scope").get(0).asString()).isEqualTo("balances:read");
    assertThat(tokens.get("refresh_token").asString()).isNotBlank();
  }

  @Test
  void approvingRecordsTheConnectionFromThePersonsBrowserAndNothingFromTheClientsServers()
      throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read", pkce);
    var lastEventBefore = lastEventLogId();

    var approval =
        mockMvc
            .perform(
                approveRequest(connectRequest, bearer(smartIdLoginNow()))
                    .with(fromAddress(BROWSER_ADDRESS))
                    .header(USER_AGENT, BROWSER))
            .andExpect(status().isOk())
            .andReturn();
    var code = redirectUriOf(approval).getQueryParams().getFirst("code");
    var tokens = exchangeCode(PORTFELLOW, PORTFELLOW_CALLBACK, code, pkce);
    refresh(PORTFELLOW, tokens.get("refresh_token").asString());

    assertThat(eventLogRepository.findAll())
        .filteredOn(event -> event.getId() > lastEventBefore)
        .extracting(EventLog::getPrincipal, EventLog::getType, EventLog::getData)
        .containsExactly(
            tuple(
                PERSONAL_CODE,
                "APP_CONNECTED",
                Map.of(
                    "clientId", PORTFELLOW,
                    "scopes", List.of("balances:read"),
                    "ipAddress", BROWSER_ADDRESS,
                    "userAgent", BROWSER)));
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

  @Test
  void aPersonSeesTheAppTheyConnectedWithWhatItMayReadAndUntilWhen() throws Exception {
    var connectedAt = Instant.now().truncatedTo(SECONDS);
    ClockHolder.setClock(Clock.fixed(connectedAt, ZoneOffset.UTC));
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "transactions:read balances:read");
    var accessTokenIssuedAt =
        Instant.ofEpochSecond(claimsOf(tokens.get("access_token").asString()).get("iat").asLong());

    assertThat(connectedApps(smartIdLoginNow()))
        .singleElement()
        .satisfies(
            app -> {
              assertThat(app)
                  .usingRecursiveComparison()
                  .ignoringFields("lastUsedAt")
                  .isEqualTo(
                      new ConnectedApp(
                          grantIdOf(tokens),
                          "Portfellow",
                          List.of("balances:read", "transactions:read"),
                          connectedAt,
                          null,
                          connectedAt.plus(30, DAYS)));
              assertThat(app.lastUsedAt().truncatedTo(SECONDS)).isEqualTo(accessTokenIssuedAt);
            });
  }

  @Test
  void anAppThePersonApprovedButThatHasNotYetFetchedAnAccessTokenIsListedAsNeverUsed()
      throws Exception {
    approve(
        startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read"), smartIdLoginNow());

    assertThat(connectedApps(smartIdLoginNow()))
        .singleElement()
        .extracting(ConnectedApp::lastUsedAt)
        .isNull();
  }

  @Test
  void anApprovalWhoseCodeTheAppNeverCollectedIsNoLongerListedOnceTheCodeExpires()
      throws Exception {
    var approvedAt = Instant.now().truncatedTo(SECONDS);
    ClockHolder.setClock(Clock.fixed(approvedAt, ZoneOffset.UTC));
    approve(
        startAuthorization(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read"), smartIdLoginNow());

    ClockHolder.setClock(Clock.fixed(approvedAt.plus(2, MINUTES), ZoneOffset.UTC));

    assertThat(connectedApps(smartIdLoginNow())).isEmpty();
  }

  @Test
  void anAppThatKeepsRefreshingIsListedUntilOneHundredEightyDaysAndThenNoLonger() throws Exception {
    var connectedAt = Instant.now().truncatedTo(SECONDS);
    ClockHolder.setClock(Clock.fixed(connectedAt, ZoneOffset.UTC));
    var refreshToken =
        connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read").get("refresh_token").asString();
    for (int day = 25; day <= 175; day += 25) {
      ClockHolder.setClock(Clock.fixed(connectedAt.plus(day, DAYS), ZoneOffset.UTC));
      refreshToken = refresh(PORTFELLOW, refreshToken).get("refresh_token").asString();
    }

    assertThat(connectedApps(smartIdLoginNow()))
        .extracting(ConnectedApp::expiresAt)
        .containsExactly(connectedAt.plus(180, DAYS));

    ClockHolder.setClock(Clock.fixed(connectedAt.plus(180, DAYS), ZoneOffset.UTC));

    assertThat(connectedApps(smartIdLoginNow())).isEmpty();
  }

  @Test
  void connectedAppsAreListedNewestFirstAndDropOffAfterThirtyDaysWithoutARefresh()
      throws Exception {
    var firstConnectedAt = Instant.now().truncatedTo(SECONDS);
    ClockHolder.setClock(Clock.fixed(firstConnectedAt, ZoneOffset.UTC));
    connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");
    ClockHolder.setClock(Clock.fixed(firstConnectedAt.plus(20, DAYS), ZoneOffset.UTC));
    connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    assertThat(connectedApps(smartIdLoginNow()))
        .extracting(ConnectedApp::clientName)
        .containsExactly("Portfellow", "Other");

    ClockHolder.setClock(Clock.fixed(firstConnectedAt.plus(30, DAYS), ZoneOffset.UTC));

    assertThat(connectedApps(smartIdLoginNow()))
        .extracting(ConnectedApp::clientName)
        .containsExactly("Portfellow");
  }

  @Test
  void aPersonWhoRevokesAConnectedAppStopsItRefreshingAndNoLongerSeesIt() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var otherTokens = connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");

    revokeConnectedApp(smartIdLoginNow(), grantIdOf(tokens)).andExpect(status().isNoContent());

    refreshExpectingInvalidGrant(PORTFELLOW, tokens.get("refresh_token").asString());
    assertThat(connectedApps(smartIdLoginNow()))
        .extracting(ConnectedApp::id)
        .containsExactly(grantIdOf(otherTokens));
    assertThat(revocationReasonOf(grantIdOf(tokens))).isEqualTo("PERSON");
  }

  @Test
  void revokingAConnectedAppThatHasAlreadyEndedSucceedsSoADoubleClickOrARaceIsHarmless()
      throws Exception {
    var connectedAt = Instant.now().truncatedTo(SECONDS);
    ClockHolder.setClock(Clock.fixed(connectedAt, ZoneOffset.UTC));
    var revokedBefore = grantIdOf(connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read"));
    var expired = grantIdOf(connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read"));
    revokeConnectedApp(smartIdLoginNow(), revokedBefore).andExpect(status().isNoContent());
    ClockHolder.setClock(Clock.fixed(connectedAt.plus(30, DAYS), ZoneOffset.UTC));

    revokeConnectedApp(smartIdLoginNow(), revokedBefore).andExpect(status().isNoContent());
    revokeConnectedApp(smartIdLoginNow(), expired).andExpect(status().isNoContent());
  }

  @Test
  void anotherPersonsConnectedAppIsNeitherListedNorRevocableAndLooksLikeAMissingOne()
      throws Exception {
    var othersTokens =
        connectAs(ANOTHER_PERSONAL_CODE, PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    assertThat(connectedApps(smartIdLoginNow())).isEmpty();
    revokeConnectedApp(smartIdLoginNow(), grantIdOf(othersTokens)).andExpect(status().isNotFound());
    revokeConnectedApp(smartIdLoginNow(), UUID.randomUUID()).andExpect(status().isNotFound());
    assertThat(
            refresh(PORTFELLOW, othersTokens.get("refresh_token").asString()).get("access_token"))
        .isNotNull();
  }

  @ParameterizedTest
  @MethodSource("rolesOfSomeoneElse")
  void aPersonActingForSomeoneElseStillSeesAndRevokesTheAppsTheyConnectedThemselves(Role role)
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var actingForSomeoneElse = loggedIn("SMART_ID", Instant.now(), role);

    assertThat(connectedApps(actingForSomeoneElse))
        .extracting(ConnectedApp::id)
        .containsExactly(grantIdOf(tokens));
    revokeConnectedApp(actingForSomeoneElse, grantIdOf(tokens)).andExpect(status().isNoContent());
    refreshExpectingInvalidGrant(PORTFELLOW, tokens.get("refresh_token").asString());
  }

  static Stream<Role> rolesOfSomeoneElse() {
    return Stream.of(
        new Role(LEGAL_ENTITY, "12345678", "Company OÜ"),
        new Role(PERSON, "60001019906", "Child Name"));
  }

  @Test
  void anAppThatTulevaSuspendedIsNoLongerListed() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");

    jdbcClient
        .sql(
            """
            INSERT INTO oauth_client_suspension (client_id, suspended_at, reason)
            VALUES (:clientId, CURRENT_TIMESTAMP, 'test')
            """)
        .param("clientId", OTHER_CLIENT)
        .update();

    assertThat(connectedApps(smartIdLoginNow()))
        .extracting(ConnectedApp::id)
        .containsExactly(grantIdOf(tokens));
  }

  @Test
  void anAppsOwnAccessTokenCannotListOrRevokeThePersonsConnectedApps() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var appAccessToken = tokens.get("access_token").asString();

    mockMvc
        .perform(get("/v1/me/connected-apps").header(AUTHORIZATION, "Bearer " + appAccessToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/v1/me/connected-apps/{id}", grantIdOf(tokens))
                .header(AUTHORIZATION, "Bearer " + appAccessToken))
        .andExpect(status().isForbidden());
    assertThat(connectedApps(smartIdLoginNow())).hasSize(1);
  }

  @Test
  void anAppWithBalancesReadGetsThePersonsSavingsFundStatementInThePublishedShape()
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    willReturn(Optional.of(savingsFundBalance()))
        .given(savingsFundStatementService)
        .getAccountStatement(thePersonActingAsThemselves());

    var response =
        appRequest(get("/v1/savings-account-statement"), tokens)
            .andExpect(status().isOk())
            .andReturn();

    assertThat(jsonOf(response)).isEqualTo(jsonMapper.readTree(PUBLISHED_SAVINGS_FUND_STATEMENT));
  }

  @Test
  void anAppWithTransactionsReadGetsThePersonsSavingsFundTransactionsInThePublishedShape()
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "transactions:read");
    willReturn(savingsFundTransactions())
        .given(savingsFundTransactionService)
        .getTransactions(thePersonActingAsThemselves());

    var response =
        appRequest(get("/v1/transactions"), tokens).andExpect(status().isOk()).andReturn();

    assertThat(jsonOf(response)).isEqualTo(jsonMapper.readTree(PUBLISHED_TRANSACTIONS));
  }

  @Test
  void anAppGetsExactlyTheSavingsFundStatementTheWebAppGetsForTheSameHolding() throws Exception {
    thePersonHoldsSavingsFundUnitsAndHasRedeemedSome();
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    var webApp = webAppRequest(get("/v1/savings-account-statement")).andReturn();
    var app = appRequest(get("/v1/savings-account-statement"), tokens).andReturn();

    assertThat(jsonOf(app)).isEqualTo(jsonOf(webApp));
  }

  @Test
  void
      anAppGetsExactlyTheSavingsFundTransactionsTheWebAppGetsWhenThePersonHasNoPensionTransactions()
          throws Exception {
    thePersonHoldsSavingsFundUnitsAndHasRedeemedSome();
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "transactions:read");
    var pensionikeskus = pensionikeskusWithNoPensionTransactions();

    var webApp = webAppRequest(get("/v1/transactions")).andReturn();
    var app = appRequest(get("/v1/transactions"), tokens).andReturn();

    pensionikeskus.verify();
    assertThat(jsonOf(app)).hasSize(2).isEqualTo(jsonOf(webApp));
  }

  @ParameterizedTest
  @MethodSource("endpointsOpenToApps")
  void noEndpointOpenToAppsAsksPensionikeskusWhileServingThePersonsSavingsFundHoldingToTheApp(
      AppEndpoint endpoint) throws Exception {
    thePersonHoldsSavingsFundUnitsAndHasRedeemedSome();
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, endpoint.scope());
    var pensionikeskus = pensionikeskusThatMustNeverBeAsked();

    appRequest(request(endpoint.method(), endpoint.path()), tokens).andExpect(status().isOk());

    pensionikeskus.forEach(MockRestServiceServer::verify);
  }

  static Stream<AppEndpoint> endpointsOpenToApps() {
    return AppEndpoint.OPEN_TO_APPS.stream();
  }

  @ParameterizedTest
  @MethodSource("anyRequestAnAppMightSend")
  void anAppAccessTokenNeverReachesTheWebAppLoginFilterWhateverItIsSentTo(
      HttpMethod method, String path) throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    assertThat(filtersServing(method, path, tokens.get("access_token").asString()))
        .noneMatch(JwtAuthorizationFilter.class::isInstance);
  }

  @Test
  void aWebAppLoginTokenIsStillHandedToTheWebAppLoginFilter() {
    assertThat(filtersServing(GET, "/v1/transactions", bearer(smartIdLoginNow())))
        .anyMatch(JwtAuthorizationFilter.class::isInstance);
  }

  static Stream<Arguments> anyRequestAnAppMightSend() {
    return Stream.of(
            endpointsNotOpenToApps(),
            endpointsOpenToApps().map(endpoint -> arguments(endpoint.method(), endpoint.path())),
            Stream.of(
                arguments(POST, "/authenticate"),
                arguments(POST, "/oauth/token"),
                arguments(POST, "/oauth2/token"),
                arguments(POST, "/oauth2/revoke"),
                arguments(GET, "/error"),
                arguments(GET, "/")))
        .flatMap(requests -> requests);
  }

  @Test
  void anAppIsRefusedTheEndpointOfAScopeItsConnectionDoesNotHold() throws Exception {
    var balancesOnly = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var transactionsOnly = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "transactions:read");

    appRequest(get("/v1/transactions"), balancesOnly).andExpect(status().isForbidden());
    appRequest(get("/v1/savings-account-statement"), transactionsOnly)
        .andExpect(status().isForbidden());
  }

  @Test
  void aScopeTulevaNoLongerConfiguresForTheAppOpensNothingOnAConnectionThatStillHoldsIt()
      throws Exception {
    var tokens = connect(OTHER_CLIENT, OTHER_CALLBACK, "balances:read");
    jdbcClient
        .sql("UPDATE oauth_grant SET scopes = 'balances:read transactions:read' WHERE id = :id")
        .param("id", grantIdOf(tokens))
        .update();
    var refreshed = refresh(OTHER_CLIENT, tokens.get("refresh_token").asString());

    assertThat(
            jsonMapper.treeToValue(
                claimsOf(refreshed.get("access_token").asString()).get("scope"), String[].class))
        .containsExactlyInAnyOrder("balances:read", "transactions:read");
    appRequest(get("/v1/transactions"), refreshed).andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @MethodSource("endpointsNotOpenToApps")
  void anAppTokenWithEveryScopeOpensNoOtherEndpointAndRecordsNoRead(HttpMethod method, String path)
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read transactions:read");
    var lastEventBefore = lastEventLogId();

    appRequest(request(method, path), tokens)
        .andExpect(status().isForbidden())
        .andExpect(header().string(WWW_AUTHENTICATE, startsWith("Bearer")));

    assertThat(eventLogRepository.findAll())
        .filteredOn(event -> event.getId() > lastEventBefore)
        .isEmpty();
  }

  static Stream<Arguments> endpointsNotOpenToApps() {
    var anyId = "4f9d2c6e-8a1b-4c3d-9e5f-6a7b8c9d0e1f";
    return Stream.of(
        arguments(GET, "/v1/me"),
        arguments(GET, "/v1/me/principal"),
        arguments(PATCH, "/v1/me"),
        arguments(GET, "/v1/pension-account-statement"),
        arguments(GET, "/v1/me/capital"),
        arguments(GET, "/v1/applications"),
        arguments(POST, "/v1/mandates"),
        arguments(GET, "/v1/me/connected-apps"),
        arguments(DELETE, "/v1/me/connected-apps/" + anyId),
        arguments(POST, "/v1/savings-account-statement"),
        arguments(POST, "/v1/t"),
        arguments(GET, "/v1/funds"),
        arguments(GET, "/v1/connect/" + anyId),
        arguments(POST, "/v1/connect/" + anyId + "/approve"));
  }

  @Test
  void everyReadAnAppMakesIsRecordedForThePersonWithoutTheAddressOfTheAppsServer()
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var lastEventBefore = lastEventLogId();

    appRequest(get("/v1/savings-account-statement").with(fromAddress(APP_SERVER_ADDRESS)), tokens)
        .andExpect(status().isNoContent());

    assertThat(eventLogRepository.findAll())
        .filteredOn(event -> event.getId() > lastEventBefore)
        .extracting(EventLog::getPrincipal, EventLog::getType, EventLog::getData)
        .containsExactly(
            tuple(
                PERSONAL_CODE,
                "APP_DATA_READ",
                Map.of(
                    "clientId",
                    PORTFELLOW,
                    "grantId",
                    grantIdOf(tokens).toString(),
                    "endpoint",
                    "GET /v1/savings-account-statement",
                    "scopes",
                    List.of("balances:read"))));
  }

  @Test
  void aReadIsRecordedUnderTheEndpointThatServedItHoweverTheAppEncodesThePath() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    var lastEventBefore = lastEventLogId();

    appRequest(get(URI.create("/v1/savings%2Daccount%2Dstatement")), tokens)
        .andExpect(status().isNoContent());

    assertThat(eventLogRepository.findAll())
        .filteredOn(event -> event.getId() > lastEventBefore)
        .extracting(event -> event.getData().get("endpoint"))
        .containsExactly("GET /v1/savings-account-statement");
  }

  @Test
  void
      aRefusedAccessTokenPointsToTheMetadataAddressOfTheApiWhichNamesTulevaAsItsAuthorizationServer()
          throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    ClockHolder.setClock(Clock.fixed(Instant.now().plus(12, MINUTES), ZoneOffset.UTC));

    var challenge =
        appRequest(get("/v1/savings-account-statement"), tokens)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getHeader(WWW_AUTHENTICATE);
    var metadataLink = resourceMetadataLinkOf(challenge);
    var metadata =
        mockMvc.perform(get(URI.create(metadataLink))).andExpect(status().isOk()).andReturn();

    assertThat(metadataLink).isEqualTo(ISSUER + "/.well-known/oauth-protected-resource/v1");
    assertThat(jsonOf(metadata))
        .isEqualTo(
            jsonMapper.readTree(
                """
                {
                  "resource": "http://localhost:9000/v1",
                  "authorization_servers": ["http://localhost:9000"],
                  "bearer_methods_supported": ["header"],
                  "tls_client_certificate_bound_access_tokens": false
                }
                """));
  }

  @Test
  void aConnectionThePersonRevokesStopsItsAccessTokenAtOnce() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");
    appRequest(get("/v1/savings-account-statement"), tokens).andExpect(status().isNoContent());

    revokeConnectedApp(smartIdLoginNow(), grantIdOf(tokens)).andExpect(status().isNoContent());

    appRequest(get("/v1/savings-account-statement"), tokens)
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(WWW_AUTHENTICATE, containsString("invalid_token")));
  }

  @Test
  void anAppThatTulevaSuspendsLosesItsAccessTokensAtOnce() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    jdbcClient
        .sql(
            """
            INSERT INTO oauth_client_suspension (client_id, suspended_at, reason)
            VALUES (:clientId, CURRENT_TIMESTAMP, 'test')
            """)
        .param("clientId", PORTFELLOW)
        .update();

    appRequest(get("/v1/savings-account-statement"), tokens).andExpect(status().isUnauthorized());
  }

  @Test
  void anAccessTokenStopsWorkingOnceItsTenMinutesAreOver() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    ClockHolder.setClock(Clock.fixed(Instant.now().plus(12, MINUTES), ZoneOffset.UTC));

    appRequest(get("/v1/savings-account-statement"), tokens).andExpect(status().isUnauthorized());
  }

  @Test
  void aServerErrorBehindAnEndpointOpenToAppsReachesTheAppAsTheErrorAndNotAsARefusal()
      throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    appRequest(get("/error").with(errorDispatchOf("/v1/savings-account-statement")), tokens)
        .andExpect(jsonPath("$.errors[0].code").value("Internal Server Error"));
  }

  @Test
  void aDirectRequestForTheErrorPageIsRefusedToAnApp() throws Exception {
    var tokens = connect(PORTFELLOW, PORTFELLOW_CALLBACK, "balances:read");

    appRequest(get("/error"), tokens).andExpect(status().isForbidden());
  }

  @Test
  void aWebLoginOnAnEndpointOpenToAppsIsStillAWebLogin() throws Exception {
    mockMvc
        .perform(
            get("/v1/savings-account-statement")
                .header(AUTHORIZATION, "Bearer " + bearer(smartIdLoginNow())))
        .andExpect(status().isNoContent())
        .andExpect(header().doesNotExist(WWW_AUTHENTICATE));
  }

  private ResultActions appRequest(MockHttpServletRequestBuilder request, JsonNode tokens)
      throws Exception {
    return mockMvc.perform(
        request
            .header(AUTHORIZATION, "Bearer " + tokens.get("access_token").asString())
            .header(ACCEPT_LANGUAGE, "en"));
  }

  private ResultActions webAppRequest(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc
        .perform(
            request
                .header(AUTHORIZATION, "Bearer " + bearer(smartIdLoginNow()))
                .header(ACCEPT_LANGUAGE, "en"))
        .andExpect(status().isOk());
  }

  private List<Filter> filtersServing(HttpMethod method, String path, String bearerToken) {
    var request = new MockHttpServletRequest(method.name(), path);
    request.addHeader(AUTHORIZATION, "Bearer " + bearerToken);
    return filterChainProxy.getFilterChains().stream()
        .filter(chain -> chain.matches(request))
        .findFirst()
        .orElseThrow()
        .getFilters();
  }

  private MockRestServiceServer pensionikeskusWithNoPensionTransactions() {
    var server = MockRestServiceServer.bindTo(episRestTemplate).bufferContent().build();
    server
        .expect(requestTo(containsString("/account-cash-flow-statement")))
        .andRespond(withSuccess("{\"transactions\": []}", APPLICATION_JSON));
    return server;
  }

  private JsonNode jsonOf(MvcResult result) throws Exception {
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  private AuthenticatedPerson thePersonActingAsThemselves() {
    return jdbcClient
        .sql("SELECT id, first_name, last_name FROM users WHERE personal_code = :personalCode")
        .param("personalCode", PERSONAL_CODE)
        .query(
            (resultSet, rowNum) ->
                AuthenticatedPerson.builder()
                    .personalCode(PERSONAL_CODE)
                    .firstName(resultSet.getString("first_name"))
                    .lastName(resultSet.getString("last_name"))
                    .userId(resultSet.getLong("id"))
                    .role(
                        new Role(
                            PERSON,
                            PERSONAL_CODE,
                            resultSet.getString("first_name")
                                + " "
                                + resultSet.getString("last_name")))
                    .build())
        .single();
  }

  private void thePersonHoldsSavingsFundUnitsAndHasRedeemedSome() {
    var person = new PartyRef(LedgerParty.PartyType.PERSON, PERSONAL_CODE);
    var navDate = LocalDate.now(ClockHolder.clock()).minusDays(7);
    var nav = new BigDecimal("10.0000");
    var payment = UUID.randomUUID();
    var redemption = UUID.randomUUID();
    savingsFundOnboardingRepository.saveOnboardingStatus(
        PERSONAL_CODE, PartyId.Type.PERSON, COMPLETED);
    savingsFundLedger.recordPaymentReceived(person, new BigDecimal("100.00"), payment);
    savingsFundLedger.reservePaymentForSubscription(person, new BigDecimal("100.00"), payment);
    savingsFundLedger.issueFundUnitsFromReserved(
        person, new BigDecimal("100.00"), new BigDecimal("10.00000"), nav, navDate, payment);
    savingsFundLedger.reserveFundUnitsForRedemption(person, new BigDecimal("1.00000"), redemption);
    savingsFundLedger.redeemFundUnitsFromReserved(
        person, new BigDecimal("1.00000"), new BigDecimal("10.00"), nav, navDate, redemption);
    jdbcClient
        .sql(
            """
            INSERT INTO index_values (key, date, value, provider, updated_at)
            VALUES (:key, :date, :value, 'EODHD', :updatedAt)
            """)
        .param("key", TKF100.getIsin())
        .param("date", navDate)
        .param("value", nav)
        .param("updatedAt", Timestamp.from(Instant.now(ClockHolder.clock())))
        .update();
  }

  private List<MockRestServiceServer> pensionikeskusThatMustNeverBeAsked() {
    return Stream.of(episRestTemplate, episLongRequestRestTemplate)
        .map(
            template -> {
              var server = MockRestServiceServer.bindTo(template).build();
              server.expect(never(), anything());
              return server;
            })
        .toList();
  }

  private static FundBalance savingsFundBalance() {
    return FundBalance.builder()
        .fund(
            Fund.builder()
                .fundManager(new FundManager("Tuleva", 1L))
                .isin("EE0000000000")
                .nameEnglish("Example Savings Fund")
                .nameEstonian("Näidisfond")
                .managementFeeRate(new BigDecimal("0.0016"))
                .ongoingChargesFigure(new BigDecimal("0.0029"))
                .status(ACTIVE)
                .inceptionDate(LocalDate.parse("2025-09-18"))
                .build())
        .currency("EUR")
        .units(new BigDecimal("100.12345"))
        .value(new BigDecimal("1234.56"))
        .unavailableUnits(new BigDecimal("0.00000"))
        .unavailableValue(new BigDecimal("0.00"))
        .contributions(new BigDecimal("1000.00"))
        .subtractions(new BigDecimal("-100.00"))
        .build();
  }

  private static List<Transaction> savingsFundTransactions() {
    return List.of(
        Transaction.builder()
            .id(UUID.fromString("7d1a9f3e-2c4b-4e8a-9b6d-0f5e3a2c1b47"))
            .amount(new BigDecimal("400.00"))
            .currency(EUR)
            .time(Instant.parse("2026-10-14T09:00:00Z"))
            .isin("EE0000000000")
            .type(TRANSFER_IN)
            .units(new BigDecimal("32.49391"))
            .acquisitionCost(new BigDecimal("350.00"))
            .build(),
        Transaction.builder()
            .id(UUID.fromString("2b8e4d1c-6f3a-4a9b-8c7d-5e1f2a3b4c5d"))
            .amount(new BigDecimal("-123.10"))
            .currency(EUR)
            .time(Instant.parse("2026-10-07T12:00:00Z"))
            .navDate(LocalDate.parse("2026-10-06"))
            .priceCalculationDate(LocalDate.parse("2026-10-07"))
            .settledTime(Instant.parse("2026-10-09T08:00:00Z"))
            .applicationTime(Instant.parse("2026-10-05T18:20:00Z"))
            .counterpartyIban(SYNTHETIC_IBAN)
            .isin("EE0000000000")
            .type(SUBTRACTION)
            .units(new BigDecimal("10.00000"))
            .nav(new BigDecimal("12.3100"))
            .build(),
        Transaction.builder()
            .id(UUID.fromString("9c3f5a7b-1d2e-4f6a-8b9c-0d1e2f3a4b5c"))
            .amount(new BigDecimal("100.00"))
            .currency(EUR)
            .time(Instant.parse("2026-10-02T14:00:00Z"))
            .navDate(LocalDate.parse("2026-10-01"))
            .priceCalculationDate(LocalDate.parse("2026-10-02"))
            .applicationTime(Instant.parse("2026-10-01T11:30:00Z"))
            .counterpartyIban(SYNTHETIC_IBAN)
            .isin("EE0000000000")
            .type(CONTRIBUTION_CASH)
            .units(new BigDecimal("8.13008"))
            .nav(new BigDecimal("12.3000"))
            .build());
  }

  private JsonNode connect(String clientId, String callback, String scope) throws Exception {
    return connectAs(PERSONAL_CODE, clientId, callback, scope);
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

  private long lastEventLogId() {
    return jdbcClient.sql("SELECT COALESCE(MAX(id), 0) FROM event_log").query(Long.class).single();
  }

  private static String resourceMetadataLinkOf(@Nullable String challenge) {
    var link = Pattern.compile("resource_metadata=\"([^\"]+)\"").matcher(String.valueOf(challenge));
    assertThat(link.find()).isTrue();
    return link.group(1);
  }

  private static RequestPostProcessor errorDispatchOf(String failedPath) {
    return request -> {
      request.setDispatcherType(DispatcherType.ERROR);
      request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, failedPath);
      request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
      return request;
    };
  }

  private static RequestPostProcessor fromAddress(String address) {
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
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
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES384), claims);
    jwt.sign(new ECDSASigner((ECPrivateKey) key));
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
    return KeyFactory.getInstance("EC")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
  }

  private static AuthenticatedPerson smartIdLoginNow() {
    return smartIdLoginNowAs(PERSONAL_CODE);
  }

  private static Role ownRole() {
    return new Role(PERSON, PERSONAL_CODE, "Jordan Valdma");
  }

  private static AuthenticatedPerson loggedIn(String grantType, Instant authTime, Role role) {
    return loggedIn(PERSONAL_CODE, grantType, authTime, role);
  }

  private static AuthenticatedPerson loggedIn(
      String personalCode, String grantType, Instant authTime, Role role) {
    return AuthenticatedPerson.builder()
        .personalCode(personalCode)
        .firstName("Jordan")
        .lastName("Valdma")
        .attributes(Map.of("grantType", grantType, "authTime", authTime.toString()))
        .role(role)
        .build();
  }

  private String bearer(AuthenticatedPerson person) {
    return jwtTokenUtil.generateAccessToken(person, List.of(new SimpleGrantedAuthority(USER)));
  }

  private JsonNode headerOf(String jwt) {
    var header = jwt.split("\\.")[0];
    return jsonMapper.readTree(new String(Base64.getUrlDecoder().decode(header), UTF_8));
  }

  private JsonNode claimsOf(String jwt) {
    var payload = jwt.split("\\.")[1];
    return jsonMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), UTF_8));
  }

  private static String sha256(String token) throws Exception {
    return java.util.HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(US_ASCII)));
  }

  private JsonNode connectAs(String personalCode, String clientId, String callback, String scope)
      throws Exception {
    var pkce = Pkce.generate();
    var connectRequest = startAuthorization(clientId, callback, scope, pkce);
    var code =
        approve(connectRequest, smartIdLoginNowAs(personalCode)).getQueryParams().getFirst("code");
    return exchangeCode(clientId, callback, code, pkce);
  }

  private List<ConnectedApp> connectedApps(AuthenticatedPerson person) throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/me/connected-apps").header(AUTHORIZATION, "Bearer " + bearer(person)))
            .andExpect(status().isOk())
            .andReturn();
    return jsonMapper.readValue(
        result.getResponse().getContentAsString(), new TypeReference<List<ConnectedApp>>() {});
  }

  private ResultActions revokeConnectedApp(AuthenticatedPerson person, UUID grantId)
      throws Exception {
    return mockMvc.perform(
        delete("/v1/me/connected-apps/{id}", grantId)
            .header(AUTHORIZATION, "Bearer " + bearer(person)));
  }

  private UUID grantIdOf(JsonNode tokens) {
    return UUID.fromString(
        claimsOf(tokens.get("access_token").asString()).get("grant_id").asString());
  }

  private String revocationReasonOf(UUID grantId) {
    return jdbcClient
        .sql("SELECT revocation_reason FROM oauth_grant WHERE id = :id")
        .param("id", grantId)
        .query(String.class)
        .single();
  }

  private static AuthenticatedPerson smartIdLoginNowAs(String personalCode) {
    return loggedIn(
        personalCode,
        "SMART_ID",
        Instant.now(ClockHolder.clock()),
        new Role(PERSON, personalCode, "Jordan Valdma"));
  }

  private record ConnectedApp(
      UUID id,
      String clientName,
      List<String> scopes,
      Instant connectedAt,
      Instant lastUsedAt,
      Instant expiresAt) {}

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
