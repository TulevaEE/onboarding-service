package ee.tuleva.onboarding.aml.sanctions


import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.user.address.Address
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
    def firstName = "Peeter"
    def lastName = "Meeter"
    def fullName = "$firstName $lastName"
    def birthDate = LocalDate.parse("1960-04-08")
    def personalCode = "36004081234"
    def person = new PersonImpl(personalCode, firstName, lastName)
    def country = "ee"
    def address = Address.builder().countryCode(country).build()
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
    def expectedQuery = """{
      "schema": "Person",
        "properties": {
          "name": ["$fullName"],
          "birthDate": ["$birthDate"],
          "country": ["ee"]
       }
    }"""
    def responseJson = """{
      "responses": {
        "$personalCode": {
          "status": 200,
          "results": ${expectedResults},
          "query": ${expectedQuery}
        }
      }
    }"""

    server.expect(requestTo("https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7&topics=role.pep&topics=role.rca&topics=sanction&facets=countries&facets=topics&facets=datasets&facets=gender"))
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
    MatchResponse response = openSanctionsService.match(person, address)

    then:
    new JsonSlurper().parseText(objectMapper.writeValueAsString(response.results())) == new JsonSlurper().parseText(expectedResults)
    new JsonSlurper().parseText(objectMapper.writeValueAsString(response.query())) == new JsonSlurper().parseText(expectedQuery)
  }

  def "handles different address scenarios correctly"() {
    given:
    def firstName = "Peeter"
    def lastName = "Meeter"
    def fullName = "$firstName $lastName"
    def birthDate = LocalDate.parse("1960-04-08")
    def personalCode = "36004081234"
    def person = new PersonImpl(personalCode, firstName, lastName)
    def expectedQuery = { countries -> """{
      "schema": "Person",
        "properties": {
          "name": ["$fullName"],
          "birthDate": ["$birthDate"],
          "country": ${countries}
       }
    }""" }
    def emptyResults = "[]"

    def makeResponse = { countries -> """{
      "responses": {
        "$personalCode": {
          "status": 200,
          "results": ${emptyResults},
          "query": ${expectedQuery(countries)}
        }
      }
    }""" }

    def makeExpectedRequest = { countries -> """
    {
        "queries": {
          "$personalCode": {
            "schema": "Person",
            "properties": {
              "name": ["$fullName"],
              "birthDate": ["$birthDate"],
              "country": ${countries},
              "gender": ["male"]
            }
          }
        }
    }""" }

    when: "address is null"
    server.expect(requestTo("https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7&topics=role.pep&topics=role.rca&topics=sanction&facets=countries&facets=topics&facets=datasets&facets=gender"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(makeExpectedRequest('["ee"]')))
        .andRespond(withSuccess(makeResponse('["ee"]'), MediaType.APPLICATION_JSON))
    def responseNull = openSanctionsService.match(person, null)

    then:
    responseNull.results().isEmpty()

    when: "address has no country code"
    server.reset()
    server.expect(requestTo("https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7&topics=role.pep&topics=role.rca&topics=sanction&facets=countries&facets=topics&facets=datasets&facets=gender"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(makeExpectedRequest('["ee"]')))
        .andRespond(withSuccess(makeResponse('["ee"]'), MediaType.APPLICATION_JSON))
    def responseNoCountry = openSanctionsService.match(person, Address.builder().build())

    then:
    responseNoCountry.results().isEmpty()

    when: "address has different country code"
    server.reset()
    def foreignAddress = Address.builder().countryCode("fi").build()
    server.expect(requestTo("https://dummyUrl/match/default?algorithm=logic-v1&threshold=0.8&cutoff=0.7&topics=role.pep&topics=role.rca&topics=sanction&facets=countries&facets=topics&facets=datasets&facets=gender"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(makeExpectedRequest('["ee","fi"]')))
        .andRespond(withSuccess(makeResponse('["ee","fi"]'), MediaType.APPLICATION_JSON))
    def responseForeign = openSanctionsService.match(person, foreignAddress)

    then:
    responseForeign.results().isEmpty()
  }

  def cleanup() {
    server.reset()
  }
}
