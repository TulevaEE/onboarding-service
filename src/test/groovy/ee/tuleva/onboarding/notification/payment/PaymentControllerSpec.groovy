package ee.tuleva.onboarding.notification.payment

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.http.MediaType
import org.springframework.validation.SmartValidator

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PaymentControllerSpec extends BaseControllerSpec {

  def userService = Mock(UserService)
  def validator = Mock(SmartValidator)
  def emailService = Mock(EmailService)
  def controller = new PaymentController(mapper, userService, validator, emailService)

  def mvc = mockMvc(controller)

  String membershipSuccessUrl = 'a_URL';

  def setup() {
    controller.membershipSuccessUrl = membershipSuccessUrl
  }

  def "incoming payment is correctly mapped to DTO, mac is validated and a member is created in the database with the correct name"() {
    given:
    def json = [
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
      "transaction": "235e8a24-c510-4c8d-9fa8-2a322ba80bb2"
    ]
    def sampleUserWithoutName = sampleUser().firstName(null).lastName(null).build()
    def sampleUser = sampleUser().build()
    
    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", mapper.writeValueAsString(json))
            .param("mac", "e9b1867c40e2a2ac3fd654ab4f26dedcbe320c48c0aec5058c8a442fc941429c8bcafa22c52240a1a645fb0abf6a05692f7b79b6cb83a41637aef0756cdb3dda"))

    then:
    perform
        .andExpect(status().isFound())
        .andExpect(redirectedUrl(membershipSuccessUrl))
    1 * validator.validate(*_)
    1 * userService.registerAsMember(1L) >> sampleUserWithoutName
    1 * userService.updateNameIfMissing(sampleUserWithoutName, json.customer_name) >> sampleUser
    1 * emailService.sendMemberNumber({ User user ->
              user.firstName == sampleUser.firstName &&
              user.lastName == sampleUser.lastName
    })
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
    perform.andExpect(status().isFound())
            .andExpect(redirectedUrl(membershipSuccessUrl))
    1 * validator.validate(*_)
    0 * userService.registerAsMember(_)
    0 * emailService.sendMemberNumber(_)
    0 * userService.updateNameIfMissing(_)
  }

  def "doesn't try to create the member more than once"() {
    given:
    def json = [
      "reference": "1",
      "status": "COMPLETED"
    ]
    1 * userService.isAMember(1L) >> true

    when:
    def perform = mvc.perform(post("/notifications/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .param("json", mapper.writeValueAsString(json))
            .param("mac", "72226bd686fd3288c53784f3297164933619877cb13be6c4da91a7201fd20f4a6378abbdeb6bdbc6601905f1f052c4227dd15b9a50f42a192cde2686d1f853af"))

    then:
    perform.andExpect(status().isFound())
    1 * validator.validate(*_)
    0 * userService.registerAsMember(_)
    0 * emailService.sendMemberNumber(_)
    0 * userService.updateNameIfMissing(_)
  }

}
