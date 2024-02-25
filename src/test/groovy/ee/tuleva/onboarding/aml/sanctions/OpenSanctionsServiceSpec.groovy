package ee.tuleva.onboarding.aml.sanctions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import spock.lang.Specification

import java.time.LocalDate

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@RestClientTest(OpenSanctionsService)
@TestPropertySource(properties = "opensanctions.url=https://dummyUrl")
class OpenSanctionsServiceSpec extends Specification {

  @Autowired
  private OpenSanctionsService openSanctionsService

  @Autowired
  private MockRestServiceServer server

  @Autowired
  private ObjectMapper objectMapper

  def "can find a match"() {
    given:
    def fullName = "Peeter Meeter"
    def birthDate = LocalDate.parse("1960-04-08")
    def personalCode = "36004081234"
    def country = "ee"
    def expectedResults = """[
      {
        "id": "Q123",
        "caption": "$fullName",
        "schema": "Person",
        "properties": {
          "gender": ["male"],
          "notes": ["Estonian politician"],
          "name": ["$fullName"],
          "country": ["ee"]
        }
      }
    ]"""
    def responseJson = """{
      "responses": {
        "$personalCode": {
          "status": 200,
          "results": ${expectedResults}
        }
      }
    }"""

    server.expect(requestTo("https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.8&topics=role.pep&topics=role.rca&topics=sanction&topics=sanction.linked&facets=countries&facets=topics&facets=datasets&facets=gender"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("""
        {
            "queries": {
              "$personalCode": {
                "schema": "Person",
                "properties": {
                  "name": [
                    "$fullName"
                  ],
                  "birthDate": [
                    "$birthDate"
                  ],
                  "country": ["ee"],
                  "gender": ["male"]
                  }
              }
          }
        }"""))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))


    when:
    JsonNode results = openSanctionsService.match(fullName, personalCode, country)

    then:
    new JsonSlurper().parseText(objectMapper.writeValueAsString(results)) == new JsonSlurper().parseText(expectedResults)
  }

  def cleanup() {
    server.reset()
  }
}
