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
    String fullName = "Peeter Meeter"
    String birthDate = "1960-04-08"
    String idNumber = "36004081234"
    String nationality = "suhh"
    String expectedResults = """[
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
    String responseJson = """{
      "responses": {
        "$idNumber": {
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
              "$idNumber": {
                "schema": "Person",
                "properties": {
                  "name": [
                    "$fullName"
                  ],
                  "birthDate": [
                    "$birthDate"
                  ],
                  "idNumber": "$idNumber",
                  "nationality": ["eu", "ee", "suhh"]
                  }
              }
          }
        }"""))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))


    when:
    JsonNode results = openSanctionsService.findMatch(fullName, birthDate, idNumber, nationality)

    then:
    new JsonSlurper().parseText(objectMapper.writeValueAsString(results)) == new JsonSlurper().parseText(expectedResults)
  }

  def cleanup() {
    server.reset()
  }
}
