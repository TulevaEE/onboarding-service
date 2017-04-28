package ee.tuleva.onboarding.notification.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class PaymentControllerSpec extends  BaseControllerSpec {

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

    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", json)
            .param("mac", "68ea0115525b8baeb569676cd14f4386af3840e321185930a5aa0428845f26f9886cb4c45369b86140b29709b029728416eb369fac7a73fff3b6ab36798f4027"))


    then:
    perform
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
        .andExpect(jsonPath('$.amount', is(100.0d)))
        .andExpect(jsonPath('$.currency', is("EUR")))
        .andExpect(jsonPath('$.customer_name', is("Tõõger Leõpäöld")))
        .andExpect(jsonPath('$.reference', is(1)))
        .andExpect(jsonPath('$.status', is("COMPLETED")))
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
}
