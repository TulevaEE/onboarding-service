package ee.tuleva.onboarding.notification.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentControllerSpec extends BaseControllerSpec {

  def userService = Mock(UserService)
  def controller = new PaymentController(mapper, userService)

  def mvc = mockMvc(controller)

  def "incoming payment is correctly mapped to DTO, mac is validated and member is created in the database"() {
    given:
    def json = """{
      "amount": "100.0",
      "currency": "EUR",
      "customer_name": "T\u00f5\u00f5ger Le\u00f5p\u00e4\u00f6ld",
      "merchant_data": null,
      "message_time": "2017-04-25T12:42:37+0000",
      "message_type": "payment_return",
      "reference": "1",
      "shop": "322a5e5e-37ee-45b1-8961-ebd00e84e209",
      "signature": "EDB6E91FD890EF86EBD6A820BBAE1E99068596776667E35F823C4CE57F79D948F68F76EAEA2E8417F0E4442BCD758EEB747102CCCE70122D3C05F50C7A596339",
      "status": "COMPLETED",
      "transaction": "235e8a24-c510-4c8d-9fa8-2a322ba80bb2"}"
    }""";
    controller.frontendUrl = 'FRONTEND_URL'

    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", json)
            .param("mac", "68ea0115525b8baeb569676cd14f4386af3840e321185930a5aa0428845f26f9886cb4c45369b86140b29709b029728416eb369fac7a73fff3b6ab36798f4027"))

    then:
    perform
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("FRONTEND_URL/steps/select-sources?isNewMember=true"))
    1 * userService.registerAsMember(1L)
  }

  def "validates mac for incoming payment"() {
    expect:
    mvc.perform(post("/notifications/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .param("json", "{}")
        .param("mac", "invalid"))
        .andExpect(status().isBadRequest())
  }

  def "member is not created when the payment status is not COMPLETED"() {
    given:
    def json = '{ "status": "PENDING" }';

    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", json)
            .param("mac", "53b1ace42be9af8667a4e2be5c82b28f9f7e217f2353888f01f9de6d7da0aea95d1913fb9345abcf03edc9c796a5178e2b2d772412280b951e7612834bcff232"))

    then:
    perform.andExpect(status().isOk())
    0 * userService.registerAsMember(_)
  }

  def "doesn't try to create the member more than once"() {
    given:
    def json = """{
      "reference": "1",
      "status": "COMPLETED"
    }""";
    1 * userService.isAMember(1L) >> true

    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", json)
            .param("mac", "0051285e24b623273b60e16a5f1327c97139c91419dcb15ea5b0f8286031cdc22ffa4399556c1a5ce14d709fe0e4a1496f01b5d1950368de29b7ae322a908879"))

    then:
    perform.andExpect(status().isOk())
    0 * userService.registerAsMember(_)
  }


}
