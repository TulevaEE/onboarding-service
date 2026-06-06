package ee.tuleva.onboarding.event

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TrackingControllerSpec extends BaseControllerSpec {

  ApplicationEventPublisher eventPublisher = Mock()
  TrackingController trackingController = new TrackingController(eventPublisher)

  def "POST /t adds tracks an event"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, trackingController)
    def data = ["1": 2]
    1 * eventPublisher.publishEvent(new TrackableEvent(sampleAuthenticatedPerson, TrackableEventType.PAGE_VIEW, data))
    expect:
    mvc.perform(post("/v1/t")
        .content("""{"type": "PAGE_VIEW", "data": {"1": 2}}""")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
  }

  def "POST /t rejects server-only event types"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, trackingController)
    0 * eventPublisher.publishEvent(_)
    expect:
    mvc.perform(post("/v1/t")
        .content("""{"type": "REPRESENT_MINOR_ROLE_SWITCH", "data": {"childPersonalCode": "61506150006"}}""")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
  }

  def "POST /t rejects missing event type with 400"() {
    given:
    def mvc = mockMvcWithAuthenticationPrincipal(sampleAuthenticatedPerson, trackingController)
    0 * eventPublisher.publishEvent(_)
    expect:
    mvc.perform(post("/v1/t")
        .content("""{"data": {"1": 2}}""")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
  }

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()
}
