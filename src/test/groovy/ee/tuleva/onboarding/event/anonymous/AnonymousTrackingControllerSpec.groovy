package ee.tuleva.onboarding.event.anonymous

import ee.tuleva.onboarding.BaseControllerSpec
import org.springframework.http.MediaType

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AnonymousTrackingControllerSpec extends BaseControllerSpec {

  AnonymousEventRepository repository = Mock()
  Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)
  AnonymousEventRateLimiter rateLimiter = new AnonymousEventRateLimiter()
  def mvc = mockMvc(new AnonymousTrackingController(repository, rateLimiter, clock))

  def "stores a failed login of a person who is not logged in"() {
    when:
    def result = mvc.perform(post("/v1/t/anonymous").contentType(MediaType.APPLICATION_JSON).content(
        """{"type": "LOGIN_FAILED", "visitId": "8f1c2d3e-aaaa", "device": "mobile",
            "data": {"method": "SMART_ID", "kind": "error", "reason": "smart.id.timeout"}}"""))

    then:
    result.andExpect(status().isNoContent())
    1 * repository.save({ AnonymousEventCommand c ->
      c.type == AnonymousEventType.LOGIN_FAILED && c.visitId == "8f1c2d3e-aaaa" && c.data.reason == "smart.id.timeout"
    }, Instant.parse("2026-11-25T10:00:00Z"))
  }

  def "refuses #why"() {
    when:
    def result = mvc.perform(post("/v1/t/anonymous").contentType(MediaType.APPLICATION_JSON).content(body))

    then:
    result.andExpect(status().isBadRequest())
    0 * repository.save(_, _)

    where:
    why                             | body
    "a type that is not on the list" | """{"type": "MANDATE_SUCCESSFUL", "visitId": "8f1c2d3e-aaaa", "data": {}}"""
    "data keys the type does not have" | """{"type": "PAGE_VIEW", "visitId": "8f1c2d3e-aaaa", "data": {"path": "/login", "name": "Mari"}}"""
    "a personal code in the data"   | """{"type": "PAGE_VIEW", "visitId": "8f1c2d3e-aaaa", "data": {"path": "/login/38001010000"}}"""
    "an email address in the data"  | """{"type": "LOGIN_FAILED", "visitId": "8f1c2d3e-aaaa", "data": {"reason": "mari@example.com"}}"""
    "a missing visit id"            | """{"type": "LOGIN_STARTED", "data": {"method": "SMART_ID"}}"""
    "an unknown device"             | """{"type": "LOGIN_STARTED", "visitId": "8f1c2d3e-aaaa", "device": "fridge", "data": {}}"""
    "a value that is not text"      | """{"type": "LOGIN_STARTED", "visitId": "8f1c2d3e-aaaa", "data": {"method": {"nested": 1}}}"""
  }

  def "answers 429 once an address sends more than the limit in a minute"() {
    given:
    def body = """{"type": "LOGIN_STARTED", "visitId": "8f1c2d3e-aaaa", "data": {"method": "SMART_ID"}}"""
    AnonymousEventRateLimiter.EVENTS_PER_MINUTE.times {
      mvc.perform(post("/v1/t/anonymous").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isNoContent())
    }

    expect:
    mvc.perform(post("/v1/t/anonymous").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
  }
}
