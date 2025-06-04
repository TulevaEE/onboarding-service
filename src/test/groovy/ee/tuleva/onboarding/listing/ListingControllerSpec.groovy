package ee.tuleva.onboarding.listing

import com.fasterxml.jackson.databind.ObjectMapper
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.listing.ListingContactPreference.EMAIL_AND_PHONE
import static ee.tuleva.onboarding.listing.ListingType.BUY
import static ee.tuleva.onboarding.listing.ListingType.SELL
import static ee.tuleva.onboarding.listing.MessageResponse.Status.QUEUED
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ListingController)
@WithMockUser
class ListingControllerSpec extends Specification {

  @Autowired
  MockMvc mvc

  @Autowired
  ObjectMapper objectMapper

  @SpringBean
  ListingService listingService = Mock()

  def "can create listings"() {
    given:
    def request = new NewListingRequest(
        BUY,
        100.00,
        4.75,
        EUR,
        Instant.parse("2030-01-01T00:00:00Z")
    )
    def listing = new ListingDto(
        1L,
        BUY,
        100.00,
        4.75,
        EUR,
        Instant.parse("2030-01-01T00:00:00Z"),
        Instant.now()
    )

    1 * listingService.createListing(request, _) >> listing

    expect:
    mvc.perform(post("/v1/listings").with(csrf())
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath('$.id').value(1))
  }

  def "can find listings"() {
    given:
    def listing = new ListingDto(
        3L,
        SELL,
        50.00,
        6.00,
        EUR,
        Instant.parse("2030-01-01T00:00:00Z"),
        Instant.now()
    )
    1 * listingService.findActiveListings() >> [listing]

    expect:
    mvc.perform(get("/v1/listings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$[0].type').value("SELL"))
  }

  def "can delete a listing"() {
    given:
    listingService.deleteListing(1L) >> {}

    expect:
    mvc.perform(delete("/v1/listings/1").with(csrf()))
        .andExpect(status().isNoContent())
  }

  def "can contact a listing owner"() {
    given:
    def request = new ContactMessageRequest("Hello", EMAIL_AND_PHONE)
    def response = new MessageResponse(10L, QUEUED)
    listingService.contactListingOwner(1L, request) >> response

    expect:
    mvc.perform(post("/v1/listings/1/contact").with(csrf())
        .contentType(APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath('$.status').value(QUEUED.name()))
  }
}
