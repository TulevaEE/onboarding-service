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
    def nationality = "suhh"
    def expectedResults = """[
      {
        "id": "Q123",
        "caption": "$fullName",
        "schema": "Person",
        "properties": {
          "gender": ["male"],
          "notes": ["Estonian politician"],
          "name": ["$fullName"],
          "nationality": ["ee"]
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

    server.expect(requestTo("https://dummyUrl/match/default?algorithm=best&fuzzy=false"))
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
                  "idNumber": "$personalCode",
                  "nationality": ["eu", "ee", "suhh"]
                  }
              }
          }
        }"""))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))


    when:
    JsonNode results = openSanctionsService.match(fullName, birthDate, personalCode, nationality)

    then:
    new JsonSlurper().parseText(objectMapper.writeValueAsString(results)) == new JsonSlurper().parseText(expectedResults)
  }

  def cleanup() {
    server.reset()
  }
}
