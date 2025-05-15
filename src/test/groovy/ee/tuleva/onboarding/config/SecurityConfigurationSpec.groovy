package ee.tuleva.onboarding.config


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.generateJwtToken
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.authority.Authority.PARTNER
import static ee.tuleva.onboarding.auth.authority.Authority.USER
import static ee.tuleva.onboarding.auth.jwt.TokenType.ACCESS
import static ee.tuleva.onboarding.auth.jwt.TokenType.HANDOVER
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
@ActiveProfiles(["test", "mock"])
class SecurityConfigurationSpec extends Specification {

  @Autowired
  MockMvc mvc

  def "PARTNER token may hit only pension-account-statement"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, HANDOVER, [PARTNER])

    expect:
    mvc.perform(get('/v1/pension-account-statement')
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status().isOk())

    mvc.perform(get('/v1/me')
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status().isForbidden())
  }

  def "USER token has unhindered access"() {
    given:
    var jwtToken = generateJwtToken(samplePerson, ACCESS, [USER])

    expect:
    mvc.perform(get('/v1/pension-account-statement')
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status().isOk())

    and:
    mvc.perform(get('/v1/me')
        .header("Authorization", "Bearer " + jwtToken))
        .andExpect(status().isOk())
  }
}
