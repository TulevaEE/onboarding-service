package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException
import ee.tuleva.onboarding.user.member.MemberRepository
import spock.lang.Shared
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def memberRepository = Mock(MemberRepository)
  def service = new UserService(userRepository, memberRepository)

  @Shared
  String personalCodeSample = "somePersonalCode"

  @Shared
  def sampleUser = sampleUser().build()
  @Shared
  def sampleUserNonMember = User.builder()
      .phoneNumber("somePhone")
      .email("someEmail")
      .personalCode(personalCodeSample)
      .active(true)

  @Shared
  def otherUserNonMember = User.builder()
      .phoneNumber("someOtherPhone")
      .email("someOtherEmail")
      .personalCode("someOtherPersonalCode")
      .active(true)


  def "get user by id"() {
    given:
    def user = sampleUser().build()
    userRepository.findById(1L) >> Optional.of(user)

    when:
    def returnedUser = service.getById(1L)

    then:
    returnedUser == user
  }

  def "can update user email and phone number based on personal code"() {
    given:
    def user = sampleUser().build()
    userRepository.findByPersonalCode(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user

    when:
    def updatedUser = service.updateUser(user.personalCode, "erko@risthein.ee", "555555")

    then:
    updatedUser.email == "erko@risthein.ee"
    updatedUser.phoneNumber == "555555"
  }

  def "can register a non member user as a member"() {
    given:
    def user = sampleUserNonMember().build()
    userRepository.findById(user.id) >> Optional.of(user)
    memberRepository.getNextMemberNumber() >> 1000
    userRepository.save(_ as User) >> {User u -> u
    }

    when:
    def returnedUser = service.registerAsMember(user.id, "${user.firstName} ${user.lastName}")
    def member = returnedUser.member.get()

    then:
    member.memberNumber == 1000
    member.user == user
  }

  def "trying to register a user who is already a member as a new member throws exception"() {
    given:
    def user = sampleUser().build()
    userRepository.findById(user.id) >> Optional.of(user)

    when:
    service.registerAsMember(user.id, "${user.firstName} ${user.lastName}")

    then:
    thrown(UserAlreadyAMemberException)
  }

  def "isAMember() works"() {
    given:
    userRepository.findById(user?.id) >> Optional.ofNullable(user)

    when:
    def result = service.isAMember(user?.id)

    then:
    result == isAMember

    where:
    user                          | isAMember
    sampleUser().build()          | true
    sampleUserNonMember().build() | false
    null                          | false
  }

  def "can create a new user and publish an event about it"() {
    given:
    def user = sampleUserNonMember.build()
    userRepository.save(_ as User) >> {User u -> u
    }

    when:
    def createdUser = service.createNewUser(user)

    then:
    createdUser == user
  }

  private User updatedUser(String personalCode, String email, String phoneNumber) {
    sampleUserNonMember.personalCode(personalCode).email(email).phoneNumber(phoneNumber).build()
  }

  private User newUser(String personalCode, String email, String phoneNumber) {
    User.builder().personalCode(personalCode).email(email).phoneNumber(phoneNumber).active(true).build()
  }
}
