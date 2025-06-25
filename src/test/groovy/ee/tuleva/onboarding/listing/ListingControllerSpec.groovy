package ee.tuleva.onboarding.listing

import com.fasterxml.jackson.databind.ObjectMapper
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.config.SecurityTestHelper.mockAuthentication
import static ee.tuleva.onboarding.listing.ListingContactPreference.EMAIL_AND_PHONE
import static ee.tuleva.onboarding.listing.ListingsFixture.activeListing
import static ee.tuleva.onboarding.listing.ListingsFixture.newListingRequest
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ListingController)
class ListingControllerSpec extends Specification {

  @Autowired
  MockMvc mvc

  @Autowired
  ObjectMapper objectMapper

  @SpringBean
  ListingService listingService = Mock()

  def "can create listings"() {
    given:
    def request = newListingRequest().build()
    def listingDto = ListingDto.from(activeListing().build())

    1 * listingService.createListing(request, _) >> listingDto

    expect:
    mvc.perform(post("/v1/listings")
        .with(csrf())
        .with(authentication(mockAuthentication()))
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(status().isCreated())
        .andExpect(jsonPath('$.id').value(listingDto.id()))
        .andExpect(jsonPath('$.type').value(listingDto.type().name()))
        .andExpect(jsonPath('$.units').value(listingDto.units().doubleValue()))
        .andExpect(jsonPath('$.pricePerUnit').value(listingDto.pricePerUnit().doubleValue()))
        .andExpect(jsonPath('$.currency').value(listingDto.currency().name()))
        .andExpect(jsonPath('$.expiryTime').value(listingDto.expiryTime().toString()))
        .andExpect(jsonPath('$.createdTime').value(listingDto.createdTime().toString()))
  }

  def "can find listings"() {
    given:
    def listingDto = ListingDto.from(activeListing().build())
    1 * listingService.findActiveListings() >> [listingDto]

    expect:
    mvc.perform(get("/v1/listings")
        .with(authentication(mockAuthentication()))
    )
        .andExpect(status().isOk())
        .andExpect(jsonPath('$[0].id').value(listingDto.id()))
        .andExpect(jsonPath('$[0].type').value(listingDto.type().name()))
        .andExpect(jsonPath('$[0].units').value(listingDto.units().doubleValue()))
        .andExpect(jsonPath('$[0].pricePerUnit').value(listingDto.pricePerUnit().doubleValue()))
        .andExpect(jsonPath('$[0].currency').value(listingDto.currency().name()))
        .andExpect(jsonPath('$[0].expiryTime').value(listingDto.expiryTime().toString()))
        .andExpect(jsonPath('$[0].createdTime').value(listingDto.createdTime().toString()))
  }

  def "can delete a listing"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    listingService.cancelListing(1L, authenticatedPerson) >> {}

    expect:
    mvc.perform(delete("/v1/listings/1")
        .with(csrf())
        .with(authentication(mockAuthentication()))
    )
        .andExpect(status().isNoContent())
  }

  def "can contact a listing owner"() {
    given:
    def request = new ContactMessageRequest("Hello", EMAIL_AND_PHONE)
    def response = new MessageResponse(10L, "QUEUED")
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    listingService.contactListingOwner(1L, request, authenticatedPerson) >> response

    expect:
    mvc.perform(post("/v1/listings/1/contact")
        .with(csrf())
        .with(authentication(mockAuthentication()))
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(status().isAccepted())
        .andExpect(jsonPath('$.status').value("QUEUED"))
  }
}
