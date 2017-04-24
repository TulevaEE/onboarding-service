package ee.tuleva.onboarding.user

import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def service = new UserService(userRepository)

  def "can update user email and phone number based on personal code"() {
    given:
    def user = sampleUser()
    userRepository.findByPersonalCode(user.personalCode) >> user
    userRepository.save(user) >> user

    when:
    def updatedUser = service.updateUser(user.personalCode, "erko@risthein.ee", "555555")

    then:
    updatedUser.email == "erko@risthein.ee"
    updatedUser.phoneNumber == "555555"
  }

}
