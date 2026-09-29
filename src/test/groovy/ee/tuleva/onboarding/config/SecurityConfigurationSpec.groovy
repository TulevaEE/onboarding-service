package ee.tuleva.onboarding.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Shared
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.generateJwtToken
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.authority.Authority.*
import static ee.tuleva.onboarding.auth.jwt.TokenType.ACCESS
import static ee.tuleva.onboarding.auth.jwt.TokenType.HANDOVER
import static org.springframework.http.HttpMethod.DELETE
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.PATCH
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpMethod.PUT
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(["test", "mock"])
@Transactional
class SecurityConfigurationSpec extends Specification {

  @Autowired
  MockMvc mvc

  @Shared
  var memberToken = generateJwtToken(samplePerson, ACCESS, [USER, MEMBER])
  @Shared
  var userToken = generateJwtToken(samplePerson, ACCESS, [USER])
  @Shared
  var wardToken = generateJwtToken(samplePerson, ACCESS, [USER, WARD])
  @Shared
  var wardMemberToken = generateJwtToken(samplePerson, ACCESS, [USER, WARD, MEMBER])

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

    where:
    method | url                                | token
    POST   | "/v1/mandates"                     | wardToken
    POST   | "/v1/mandates"                     | wardMemberToken
    PUT    | "/v1/mandates/1/signature/smartId" | wardToken
    PATCH  | "/v1/me"                           | wardToken
    DELETE | "/v1/mandates/1"                   | wardToken
    POST   | "/v1/listings"                     | wardMemberToken
    DELETE | "/v1/listings/1"                   | wardMemberToken
    POST   | "/v1/hackathon-registration"       | wardMemberToken
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
}
