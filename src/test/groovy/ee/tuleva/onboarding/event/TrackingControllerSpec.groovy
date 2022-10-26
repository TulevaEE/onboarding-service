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

  AuthenticatedPerson sampleAuthenticatedPerson = AuthenticatedPerson.builder()
      .firstName("Jordan")
      .lastName("Valdma")
      .personalCode("38501010000")
      .userId(2L)
      .build()
}
