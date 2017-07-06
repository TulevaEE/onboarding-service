package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.notification.mailchimp.MailChimpService
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException
import ee.tuleva.onboarding.user.member.MemberRepository
import spock.lang.Shared
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def memberRepository = Mock(MemberRepository)
  def mailChimpService = Mock(MailChimpService)
  def service = new UserService(userRepository, memberRepository, mailChimpService)

  @Shared def sampleUser = sampleUser().build()
  @Shared def sampleUserNonMember = User.builder()
          .phoneNumber("somePhone")
          .email("someEmail")
          .personalCode("somePersonalCode")

  @Shared def otherUserNonMember = User.builder()
          .phoneNumber("someOtherPhone")
          .email("someOtherEmail")
          .personalCode("someOtherPersonalCode")


  def "get user by id"() {
    given:
    def user = sampleUser().build()
    userRepository.findOne(1L) >> user

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
    userRepository.findOne(user.id) >> user
    memberRepository.getNextMemberNumber() >> 1000
    userRepository.save(_ as User) >> { User u -> u }

    when:
    def returnedUser = service.registerAsMember(user.id)
    def member = returnedUser.member.get()

    then:
    member.memberNumber == 1000
    member.user == user
  }

  def "trying to register a user who is already a member as a new member throws exception"() {
    given:
    def user = sampleUser().build()
    userRepository.findOne(user.id) >> user

    when:
    service.registerAsMember(user.id)

    then:
    thrown(UserAlreadyAMemberException)
  }

  def "isAMember() works"() {
    given:
    userRepository.findOne(user?.id) >> user

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

  def "updating a user also updates it in Mailchimp"() {
    given:
    def user = sampleUser().build()
    userRepository.findByPersonalCode(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user

    when:
    service.updateUser(user.personalCode, "erko@risthein.ee", "555555")

    then:
    1 * mailChimpService.createOrUpdateMember(user)
  }

  def "registering a user as a member also updates Mailchimp"() {
    given:
    def user = sampleUserNonMember().build()
    userRepository.findOne(user.id) >> user
    userRepository.save(_ as User) >> { User u -> u }

    when:
    service.registerAsMember(user.id)

    then:
    1 * mailChimpService.createOrUpdateMember(user)
  }

  def "correctly updates user name"() {
    given:
    def user = sampleUser().firstName(null).lastName(null).build()
    userRepository.save(_ as User) >> { User u -> u }

    when:
    def updatedUser = service.updateNameIfMissing(user, givenFullName)

    then:
    updatedUser.firstName == expectedFirstName
    updatedUser.lastName == expectedLastName

    where:
    givenFullName     || expectedFirstName | expectedLastName
    null              || null              | null
    ""                || ""                | ""
    "Erko"            || "Erko"            | ""
    "ERKO RISTHEIN"   || "Erko"            | "Risthein"
    "John Jack Smith" || "John Jack"       | "Smith"
  }

  def "doesn't try to update user name when it already exists"() {
    given:
    def user = sampleUser().build()

    when:
    def updatedUser = service.updateNameIfMissing(user, "New Name")

    then:
    updatedUser.firstName == user.firstName
    updatedUser.lastName == user.lastName
  }

  def "never overwrites existing member data"() {
    given:
    userRepository.findByPersonalCode(sampleUser.personalCode) >> Optional.ofNullable(userByPersonalCode)
    userRepository.findByEmail(sampleUser.email) >> Optional.ofNullable(userByEmail)

    when:
    service.createOrUpdateUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)

    then:
    0 * userRepository.save(_)
    thrown UserAlreadyAMemberException

    where:
    userByEmail | userByPersonalCode
    sampleUser  | null
    sampleUser  | sampleUser
    null        | sampleUser
  }

  def "correctly overwrites existing non-member users in the database"() {
    given:
    userRepository.findByPersonalCode(sampleUser.personalCode) >> Optional.ofNullable(userByPersonalCode)
    userRepository.findByEmail(sampleUser.email) >> Optional.ofNullable(userByEmail)
    userRepository.save(_ as User) >> { User u -> u }

    when:
    def returnedUser = service.createOrUpdateUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)

    then:
    returnedUser == expectedUser

    where:
    userByEmail                 | userByPersonalCode          | expectedUser
    null                        | null                        | newUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)
    sampleUserNonMember.build() | null                        | updatedUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)
    null                        | sampleUserNonMember.build() | newUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)
    sampleUserNonMember.build() | sampleUserNonMember.build() | updatedUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)
    sampleUserNonMember.build() | otherUserNonMember.build()  | updatedUser(sampleUser.personalCode, sampleUser.email, sampleUser.phoneNumber)
  }

  private User updatedUser(String personalCode, String email, String phoneNumber) {
    sampleUserNonMember.personalCode(personalCode).email(email).phoneNumber(phoneNumber).build()
  }

  private User newUser(String personalCode, String email, String phoneNumber) {
    User.builder().personalCode(personalCode).email(email).phoneNumber(phoneNumber).active(true).build()
  }
}
