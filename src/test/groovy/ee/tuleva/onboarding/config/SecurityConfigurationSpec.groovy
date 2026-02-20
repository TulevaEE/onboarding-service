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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
}
