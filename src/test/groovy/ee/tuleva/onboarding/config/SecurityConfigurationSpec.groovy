package ee.tuleva.onboarding.config

import ee.tuleva.onboarding.auth.TokenService
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil
import ee.tuleva.onboarding.auth.principal.Person
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.auth.principal.PrincipalService
import ee.tuleva.onboarding.auth.role.Role
import ee.tuleva.onboarding.config.ReadOnlyForWards.WardIsReadOnly
import ee.tuleva.onboarding.time.ClockHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import spock.lang.Shared
import spock.lang.Specification

import java.security.Principal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.function.Supplier

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.generateJwtToken
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.authority.Authority.*
import static ee.tuleva.onboarding.auth.jwt.TokenType.ACCESS
import static ee.tuleva.onboarding.auth.jwt.TokenType.HANDOVER
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON
import static org.springframework.http.HttpMethod.DELETE
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.HEAD
import static org.springframework.http.HttpMethod.PATCH
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpMethod.PUT
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(["test", "mock"])
@Transactional
class SecurityConfigurationSpec extends Specification {

  @Autowired
  MockMvc mvc

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping

  @Autowired
  FilterChainProxy filterChainProxy

  @Autowired
  JwtTokenUtil jwtTokenUtil

  @Autowired
  PrincipalService principalService

  @Autowired
  TokenService tokenService

  static final Set<String> PUBLIC_ROUTES_THAT_READ_THE_LOGGED_IN_PERSON = [] as Set

  @Shared
  var memberToken = generateJwtToken(samplePerson, ACCESS, [USER, MEMBER])
  @Shared
  var userToken = generateJwtToken(samplePerson, ACCESS, [USER])
  @Shared
  var wardToken = generateJwtToken(samplePerson, ACCESS, [USER, WARD])
  @Shared
  var wardMemberToken = generateJwtToken(samplePerson, ACCESS, [USER, WARD, MEMBER])

  static final Instant WHILE_THE_MINOR_IS_ELEVEN = Instant.parse("2026-10-26T10:00:00Z")

  static final Person MINOR_CHILD = PersonImpl.builder()
      .personalCode("61506150006")
      .firstName("Mari")
      .lastName("Maasikas")
      .build()

  def cleanup() {
    ClockHolder.setDefaultClock()
  }

  def "PARTNER token may hit only specific endpoints"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, HANDOVER, [PARTNER])

    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status)

    where:
    url                               | status
    "/v1/pension-account-statement"   | status().isOk()
    "/v1/me"                          | status().isOk()
    "/v1/applications?status=PENDING" | status().isForbidden()
  }

  def "USER token has unhindered access"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, ACCESS, [USER])

    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status)

    where:
    url                               | status
    "/v1/pension-account-statement"   | status().isOk()
    "/v1/me"                          | status().isOk()
    "/v1/applications?status=PENDING" | status().isOk()
  }

  def "invalid token combinations has no extra access"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, ACCESS, [PARTNER])

    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status)

    where:
    url                               | status
    "/v1/pension-account-statement"   | status().isOk()
    "/v1/me"                          | status().isOk()
    "/v1/applications?status=PENDING" | status().isForbidden()
  }

  def "partner cannot add any extra authorities"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, HANDOVER, [PARTNER, USER])

    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status)

    where:
    url                               | status
    "/v1/pension-account-statement"   | status().isOk()
    "/v1/me"                          | status().isOk()
    "/v1/applications?status=PENDING" | status().isForbidden()
  }

  def "member has access to listings"() {
    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + token))
        .andExpect(status)

    where:
    url            | token       | status
    "/v1/listings" | memberToken | status().isOk()
    "/v1/listings" | userToken   | status().isForbidden()
  }

  def "WARD token reads everything a USER token reads"() {
    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())

    where:
    url                               | token
    "/v1/pension-account-statement"   | wardToken
    "/v1/me"                          | wardToken
    "/v1/applications?status=PENDING" | wardToken
    "/v1/pension-account-statement"   | wardMemberToken
    "/v1/me"                          | wardMemberToken
    "/v1/applications?status=PENDING" | wardMemberToken
    "/v1/listings"                    | wardMemberToken
  }

  def "WARD token cannot transact"() {
    expect:
    mvc.perform(request(method, url)
        .header("Authorization", "Bearer " + token)
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath('$.error').value("READ_ONLY_ACCESS"))

    where:
    method | url                                                           | token
    POST   | "/v1/mandates"                                                | wardToken
    POST   | "/v1/mandates"                                                | wardMemberToken
    PUT    | "/v1/mandates/1/signature/smart-id"                           | wardToken
    PUT    | "/v1/mandates/1/signature/mobile-id"                          | wardToken
    PUT    | "/v1/mandates/1/signature/id-card"                            | wardToken
    PUT    | "/v1/mandates/1/signature/id-card/signature"                  | wardToken
    PUT    | "/v1/mandate-batches/1/signature/id-card"                     | wardToken
    PUT    | "/v1/mandate-batches/1/signature/id-card/signature"           | wardToken
    PUT    | "/v1/capital-transfer-contracts/1/signature/id-card"          | wardMemberToken
    PUT    | "/v1/capital-transfer-contracts/1/signature/id-card/signature"| wardMemberToken
    PATCH  | "/v1/me"                                                      | wardToken
    DELETE | "/v1/mandates/1"                                              | wardToken
    POST   | "/v1/listings"                                                | wardMemberToken
    DELETE | "/v1/listings/1"                                              | wardMemberToken
    POST   | "/v1/hackathon-registration"                                  | wardMemberToken
  }

  def "WARD token cannot finalise a signature or start a payment through a GET"() {
    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath('$.error').value("READ_ONLY_ACCESS"))

    where:
    url                                                         | token
    "/v1/mandates/1/signature/smart-id/status"                  | wardToken
    "/v1/mandates/1/signature/mobile-id/status"                 | wardToken
    "/v1/mandates/1/signature/id-card/status"                   | wardToken
    "/v1/mandate-batches/1/signature/smart-id/status"           | wardToken
    "/v1/mandate-batches/1/signature/mobile-id/status"          | wardToken
    "/v1/mandate-batches/1/signature/id-card/status"            | wardToken
    "/v1/insurance-transfer-forms/1/signature/smart-id/status"  | wardToken
    "/v1/insurance-transfer-forms/1/signature/mobile-id/status" | wardToken
    "/v1/insurance-transfer-forms/1/signature/id-card/status"   | wardToken
    "/v1/capital-transfer-contracts/1/signature/smart-id/status"  | wardMemberToken
    "/v1/capital-transfer-contracts/1/signature/mobile-id/status" | wardMemberToken
    "/v1/capital-transfer-contracts/1/signature/id-card/status"   | wardMemberToken
    "/v1/payments/link?type=SAVINGS&amount=10&currency=EUR"     | wardToken
    "/v1/kyb/surveys/initial-validation?registry-code=12345678" | wardToken
  }

  def "WARD token cannot finalise a signature through a HEAD either"() {
    expect:
    mvc.perform(request(HEAD, "/v1/mandates/1/signature/smart-id/status")
        .header("Authorization", "Bearer " + wardToken))
        .andExpect(status().isForbidden())
  }

  def "WARD token may still send telemetry"() {
    expect:
    mvc.perform(post("/v1/t")
        .header("Authorization", "Bearer " + wardToken)
        .contentType(APPLICATION_JSON)
        .content('{"type":"PAGE_VIEW","data":{"path":"/account"}}'))
        .andExpect(status().isOk())
  }

  def "a USER token passes the ward rule on a signature status GET"() {
    expect:
    decisionFor(authenticationOf(userToken), GET, "/v1/mandates/1/signature/smart-id/status").granted
  }

  def "a member still needs membership to reach a capital transfer signature status"() {
    when:
    def decision = decisionFor(authenticationOf(userToken), GET, "/v1/capital-transfer-contracts/1/signature/smart-id/status")

    then:
    !decision.granted
    !(decision instanceof WardIsReadOnly)
  }

  def "a parent acting as their minor child is issued no WARD and passes the ward rules"() {
    given:
    def parent = principalService.getFrom(samplePerson, [:])
    def parentActingAsChild = principalService.withRole(parent, new Role(PERSON, MINOR_CHILD.personalCode, "Mari Maasikas"))
    def accessToken = tokenService.generateTokens(parentActingAsChild).accessToken()

    expect:
    jwtTokenUtil.getAuthorities(accessToken) == [USER]
    decisionFor(authenticationOf(accessToken), POST, "/v1/mandates").granted
    decisionFor(authenticationOf(accessToken), GET, "/v1/mandates/1/signature/smart-id/status").granted
  }

  def "the same token issuance makes the minor acting as themselves a WARD"() {
    given:
    ClockHolder.setClock(Clock.fixed(WHILE_THE_MINOR_IS_ELEVEN, ZoneOffset.UTC))
    def child = principalService.getFrom(MINOR_CHILD, [:], new Role(PERSON, MINOR_CHILD.personalCode, "Mari Maasikas"))
    def accessToken = tokenService.generateTokens(child).accessToken()

    expect:
    jwtTokenUtil.getAuthorities(accessToken) == [USER, WARD]
    decisionFor(authenticationOf(accessToken), POST, "/v1/mandates") instanceof WardIsReadOnly
  }

  def "with minor self-service disabled a minor's partner handover still reads while their access token reads as expired"() {
    given:
    ClockHolder.setClock(Clock.fixed(WHILE_THE_MINOR_IS_ELEVEN, ZoneOffset.UTC))

    expect:
    mvc.perform(get("/v1/me")
        .header("Authorization", "Bearer " + generateJwtToken(MINOR_CHILD, tokenType, authorities)))
        .andExpect(status)

    where:
    tokenType | authorities   | status
    HANDOVER  | [PARTNER]     | status().isOk()
    ACCESS    | [USER, WARD]  | status().isUnauthorized()
  }

  def "telemetry still needs a USER token"() {
    given:
    def partnerToken = generateJwtToken(samplePerson, HANDOVER, [PARTNER])
    def serviceToken = jwtTokenUtil.generateServiceToken()

    expect:
    [partnerToken, serviceToken].each { token ->
      mvc.perform(post("/v1/t")
          .header("Authorization", "Bearer " + token)
          .contentType(APPLICATION_JSON)
          .content('{"type":"PAGE_VIEW","data":{"path":"/account"}}'))
          .andExpect(status().isForbidden())
    }
  }

  def "a SERVICE token reaches none of a person's routes"() {
    expect:
    mvc.perform(request(method, url)
        .header("Authorization", "Bearer " + jwtTokenUtil.generateServiceToken())
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(not(containsString("READ_ONLY_ACCESS"))))

    where:
    method | url
    GET    | "/v1/me"
    POST   | "/v1/mandates"
    GET    | "/v1/mandates/1/signature/smart-id/status"
  }

  def "every GET a ward is let through is known to only read"() {
    given:
    def wardGets = routes(GET).findAll { pattern -> !isPublic(GET, pattern) && grantsAWard(GET, pattern) }

    expect:
    wardGets as Set == ReadOnlyGets.PATTERNS
  }

  def "a ward is refused a HEAD wherever it is refused the GET"() {
    given:
    def refusedGets = routes(GET).findAll { pattern -> !grantsAWard(GET, pattern) }

    expect:
    refusedGets.findAll { pattern -> grantsAWard(HEAD, pattern) }.isEmpty()
  }

  def "no public route reads who is logged in"() {
    given:
    def publicRoutesReadingThePerson = handlerMapping.handlerMethods.findAll { info, handler ->
      readsTheLoggedInPerson(handler) && methodsOf(info).any { method ->
        info.pathPatternsCondition.patternValues.any { pattern -> isPublic(method, pattern) }
      }
    }.collect { info, handler -> "${handler.beanType.simpleName}.${handler.method.name}".toString() } as Set

    expect:
    publicRoutesReadingThePerson == PUBLIC_ROUTES_THAT_READ_THE_LOGGED_IN_PERSON
  }

  def "a ward may write nowhere but telemetry, the login and public routes"() {
    given:
    def wardWrites = [POST, PUT, PATCH, DELETE].collectMany { method ->
      routes(method).findAll { pattern -> !isPublic(method, pattern) && grantsAWard(method, pattern) }
          .collect { pattern -> "$method $pattern".toString() }
    }

    expect:
    wardWrites as Set == ["POST /v1/t", "POST /login"] as Set
  }

  def "a USER token is not stopped by the write rule"() {
    expect:
    mvc.perform(post("/v1/mandates")
        .header("Authorization", "Bearer " + userToken)
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest())
  }

  def "writing to a member endpoint still needs membership"() {
    expect:
    mvc.perform(post(url)
        .header("Authorization", "Bearer " + userToken)
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isForbidden())

    where:
    url << ["/v1/listings", "/v1/hackathon-registration"]
  }

  def "a stranger reaches the gift link routes without a token"() {
    expect:
    mvc.perform(request(method, url)
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status)

    where:
    method | url                                   | status
    GET    | "/v1/gift-links/no-such-token"          | status().isNotFound()
    POST   | "/v1/gift-links/no-such-token/payments" | status().isBadRequest()
  }

  def "a member may still write to a member endpoint"() {
    expect:
    mvc.perform(post("/v1/hackathon-registration")
        .header("Authorization", "Bearer " + memberToken)
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest())
  }

  def "the load balancer and container health checks reach the probes without a token, and only readiness waits for the trust list"() {
    expect:
    mvc.perform(get(url))
        .andExpect(status)

    where:
    url                          | status
    "/actuator/health"           | status().isOk()
    "/actuator/health/liveness"  | status().isOk()
    "/actuator/health/readiness" | status().isServiceUnavailable()
  }

  def "logging out returns 200"() {
    expect:
    mvc.perform(get("/v1/logout")
        .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
  }

  def "only members can reach hackathon registration"() {
    expect:
    mvc.perform(get(url)
        .header("Authorization", "Bearer " + token))
        .andExpect(status)

    where:
    url                          | token       | status
    "/v1/hackathon-registration" | userToken   | status().isForbidden()
    "/v1/hackathon-registration" | memberToken | status().isOk()
    "/v1/hackathon-ideas"        | userToken   | status().isForbidden()
    "/v1/hackathon-ideas"        | memberToken | status().isOk()
  }

  private List<String> routes(HttpMethod method) {
    handlerMapping.handlerMethods.keySet()
        .findAll { info -> info.methodsCondition.methods.isEmpty() || info.methodsCondition.methods*.asHttpMethod().contains(method) }
        .collectMany { info -> info.pathPatternsCondition.patternValues }
        .unique()
  }

  private static List<HttpMethod> methodsOf(RequestMappingInfo info) {
    def methods = info.methodsCondition.methods
    methods.isEmpty() ? [GET, HEAD, POST, PUT, PATCH, DELETE] : methods*.asHttpMethod()
  }

  private static boolean readsTheLoggedInPerson(HandlerMethod handler) {
    handler.methodParameters.any { parameter ->
      parameter.hasParameterAnnotation(AuthenticationPrincipal) ||
          parameter.hasParameterAnnotation(CurrentSecurityContext) ||
          [Principal, Authentication, Person].any { type -> type.isAssignableFrom(parameter.parameterType) }
    }
  }

  private Authentication authenticationOf(String accessToken) {
    def authorities = jwtTokenUtil.getAuthorities(accessToken).collect { new SimpleGrantedAuthority(it) }
    new UsernamePasswordAuthenticationToken(jwtTokenUtil.getPersonFromToken(accessToken), accessToken, authorities)
  }

  private boolean isPublic(HttpMethod method, String pattern) {
    grants(new AnonymousAuthenticationToken("anonymous", "anonymousUser", [new SimpleGrantedAuthority("ROLE_ANONYMOUS")]), method, pattern)
  }

  private boolean grantsAWard(HttpMethod method, String pattern) {
    def authorities = [USER, WARD, MEMBER].collect { new SimpleGrantedAuthority(it) }
    grants(new UsernamePasswordAuthenticationToken(samplePerson, wardMemberToken, authorities), method, pattern)
  }

  private boolean grants(Authentication authentication, HttpMethod method, String pattern) {
    decisionFor(authentication, method, pattern)?.granted ?: false
  }

  private AuthorizationResult decisionFor(Authentication authentication, HttpMethod method, String pattern) {
    def request = new MockHttpServletRequest(method.name(), pattern.replaceAll(/\{[^}]+}/, "1"))
    authorizationManagerFor(request).authorize({ authentication } as Supplier<Authentication>, request)
  }

  private AuthorizationManager<HttpServletRequest> authorizationManagerFor(HttpServletRequest request) {
    def chain = filterChainProxy.filterChains.find { it.matches(request) }
    (chain.filters.find { it instanceof AuthorizationFilter } as AuthorizationFilter).authorizationManager
  }
}
