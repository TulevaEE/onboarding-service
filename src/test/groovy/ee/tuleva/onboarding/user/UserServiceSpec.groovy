package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.MemberCreatedEvent
import ee.tuleva.onboarding.user.exception.DuplicateEmailException
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException
import ee.tuleva.onboarding.user.member.MemberRepository
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Shared
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.*

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def memberRepository = Mock(MemberRepository)
  def applicationEventPublisher = Mock(ApplicationEventPublisher)
  def service = new UserService(userRepository, memberRepository, applicationEventPublisher)

  @Shared
  String personalCodeSample = "somePersonalCode"

  @Shared
  def sampleUserNonMember = User.builder()
      .phoneNumber("somePhone")
      .email("someEmail")
      .personalCode(personalCodeSample)
      .active(true)

  def "get user by id"() {
    given:
    def user = sampleUser().build()
    userRepository.findById(1L) >> Optional.of(user)

    when:
    def returnedUser = service.getById(1L).get()

    then:
    returnedUser == user
  }

  def "get user by id or throw returns the user when found"() {
    given:
    def user = sampleUser().build()
    userRepository.findById(1L) >> Optional.of(user)

    expect:
    service.getByIdOrThrow(1L) == user
  }

  def "get user by id or throw throws when not found"() {
    given:
    userRepository.findById(1L) >> Optional.empty()

    when:
    service.getByIdOrThrow(1L)

    then:
    thrown(NoSuchElementException)
  }

  def "get user by member id returns the user when found"() {
    given:
    def user = sampleUser().build()
    userRepository.findByMember_Id(1L) >> Optional.of(user)

    expect:
    service.getByMemberId(1L) == user
  }

  def "get user by member id throws when not found"() {
    given:
    userRepository.findByMember_Id(1L) >> Optional.empty()

    when:
    service.getByMemberId(1L)

    then:
    thrown(IllegalStateException)
  }

  def "can update user email and phone number based on personal code"() {
    given:
    def user = sampleUser().build()
    userRepository.findByPersonalCode(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user
    def newEmail = "erko@risthein.ee"
    userRepository.findByEmail(newEmail) >> Optional.empty()

    when:
    def updatedUser = service.updateUser(user.personalCode, Optional.of(newEmail), "555555")

    then:
    updatedUser.email == newEmail
    updatedUser.phoneNumber == "555555"
  }

  def "will get a clear exception when duplicate e-mail"() {
    given:
    def user = sampleUser().build()
    userRepository.findByPersonalCode(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user

    def userWithExistingEmail = simpleUser().build()
    def newEmail = userWithExistingEmail.email
    userRepository.findByEmail(newEmail) >> Optional.of(userWithExistingEmail)

    when:
    service.updateUser(user.personalCode, Optional.of(newEmail), "555555")

    then:
    thrown(DuplicateEmailException)
  }

  def "throws when updating a user that does not exist"() {
    given:
    userRepository.findByPersonalCode(personalCodeSample) >> Optional.empty()

    when:
    service.updateUser(personalCodeSample, Optional.empty(), null)

    then:
    def exception = thrown(RuntimeException)
    exception.class == RuntimeException
  }

  def "can register a non member user as a member"() {
    given:
    def user = sampleUserNonMember().build()
    userRepository.findById(user.id) >> Optional.of(user)
    memberRepository.getNextMemberNumber() >> 1000
    userRepository.save(_ as User) >> {User u -> u
    }

    when:
    def returnedUser = service.registerAsMember(user.id)
    def member = returnedUser.memberOrThrow

    then:
    member.memberNumber == 1000
    member.user == user
    1 * applicationEventPublisher.publishEvent( _ as MemberCreatedEvent) >> { MemberCreatedEvent event ->
      assert event.user == user
    }
  }

  def "trying to register a user who is already a member as a new member throws exception"() {
    given:
    def user = sampleUser().build()
    userRepository.findById(user.id) >> Optional.of(user)

    when:
    service.registerAsMember(user.id)

    then:
    thrown(UserAlreadyAMemberException)
  }

  def "throws when registering a nonexistent user as a member"() {
    given:
    userRepository.findById(1L) >> Optional.empty()

    when:
    service.registerAsMember(1L)

    then:
    thrown(IllegalStateException)
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

  def "returns the concurrently created user when the personal code is already taken"() {
    given:
    def user = sampleUserNonMember.build()
    def winner = sampleUserNonMember.build()
    userRepository.save(_ as User) >> { throw new DataIntegrityViolationException("duplicate key") }
    userRepository.findByPersonalCode(user.personalCode) >> Optional.of(winner)

    when:
    def createdUser = service.createNewUser(user)

    then:
    createdUser == winner
  }

  def "rethrows when the personal code is taken but the user cannot be found"() {
    given:
    def user = sampleUserNonMember.build()
    userRepository.save(_ as User) >> { throw new DataIntegrityViolationException("duplicate key") }
    userRepository.findByPersonalCode(user.personalCode) >> Optional.empty()

    when:
    service.createNewUser(user)

    then:
    thrown(DataIntegrityViolationException)
  }

  def "isExistingEmail returns correct results"() {
    given:
    def email = 'test@test.com'
    userRepository.findByEmail(email) >> existingUser

    when:
    def result = service.isExistingEmail(personalCode, Optional.of(email))

    then:
    result == isExistingEmail

    where:
    personalCode                      | existingUser                      | isExistingEmail
    '37612349128'                     | Optional.of(simpleUser().build()) | true
    '37612349128'                     | Optional.empty()                  | false
    simpleUser().build().personalCode | Optional.of(simpleUser().build()) | false
  }

  def "isExistingEmail returns false when no email is given"() {
    expect:
    !service.isExistingEmail(personalCodeSample, Optional.empty())
  }

}
