package ee.tuleva.onboarding.listing

import com.fasterxml.jackson.databind.ObjectMapper
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.config.SecurityTestHelper.mockAuthentication
import static ee.tuleva.onboarding.listing.ListingsFixture.activeListing
import static ee.tuleva.onboarding.listing.ListingsFixture.newListingRequest
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content

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
    var anUser = sampleUser().build()
    def request = newListingRequest().build()
    def listingDto = ListingDto.from(activeListing().build(), anUser)

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
        .andExpect(jsonPath('$.bookValue').value(listingDto.bookValue().doubleValue()))
        .andExpect(jsonPath('$.totalPrice').value(listingDto.totalPrice().doubleValue()))
        .andExpect(jsonPath('$.currency').value(listingDto.currency().name()))
        .andExpect(jsonPath('$.expiryTime').value(listingDto.expiryTime().toString()))
        .andExpect(jsonPath('$.createdTime').value(listingDto.createdTime().toString()))
  }

  def "can create listing with integer amounts"() {
    given:
    var anUser = sampleUser().build()
    def request = newListingRequest().bookValue(new BigDecimal("5000")).totalPrice(new BigDecimal("20000")).build()
    def listingDto = ListingDto.from(activeListing().build(), anUser)

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
        .andExpect(jsonPath('$.bookValue').value(listingDto.bookValue().doubleValue()))
        .andExpect(jsonPath('$.totalPrice').value(listingDto.totalPrice().doubleValue()))
        .andExpect(jsonPath('$.currency').value(listingDto.currency().name()))
        .andExpect(jsonPath('$.expiryTime').value(listingDto.expiryTime().toString()))
        .andExpect(jsonPath('$.createdTime').value(listingDto.createdTime().toString()))
  }

  def "can find listings"() {
    given:
    var anUser = sampleUser().build()

    var listingDto = ListingDto.from(activeListing().build(), anUser)
    1 * listingService.findActiveListings(_) >> [listingDto]

    expect:
    mvc.perform(get("/v1/listings")
        .with(authentication(mockAuthentication()))
    )
        .andExpect(status().isOk())
        .andExpect(jsonPath('$[0].id').value(listingDto.id()))
        .andExpect(jsonPath('$[0].type').value(listingDto.type().name()))
        .andExpect(jsonPath('$[0].bookValue').value(listingDto.bookValue().doubleValue()))
        .andExpect(jsonPath('$[0].totalPrice').value(listingDto.totalPrice().doubleValue()))
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
    def request = new ContactMessageRequest(true, true)
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

  def "can get preview message"() {
    given:
    def request = new ContactMessageRequest(true, true)
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    listingService.getContactMessage(1L, request, authenticatedPerson) >> "Test message"

    expect:
    mvc.perform(post("/v1/listings/1/preview-message")
        .with(csrf())
        .with(authentication(mockAuthentication()))
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
        .andExpect(status().isOk())
        .andExpect(content().string("Test message"))
  }
}
