package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.MemberCreatedEvent
import ee.tuleva.onboarding.user.contact.ContactChangeRecorder
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
  def contactChangeRecorder = Mock(ContactChangeRecorder)
  def service = new UserService(userRepository, memberRepository, applicationEventPublisher, contactChangeRecorder)

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

  def "changes user email and phone number of the user locked for update"() {
    given:
    def user = sampleUser().email("old@example.com").phoneNumber("+37255555555").build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)
    userRepository.findByEmail("new@example.com") >> Optional.empty()

    when:
    def updatedUser = service.changeContactDetails(user.personalCode, "new@example.com", "+37251111111")

    then:
    1 * userRepository.save(user) >> user

    then:
    1 * contactChangeRecorder.recordSelfServiceChange(user, "old@example.com", "+37255555555")
    updatedUser.email == "new@example.com"
    updatedUser.phoneNumber == "+37251111111"
  }

  def "locks the user before looking up whether the e-mail is taken so the user is not read unlocked first"() {
    given:
    def user = sampleUser().email("old@example.com").phoneNumber("+37255555555").build()
    userRepository.save(user) >> user

    when:
    service.changeContactDetails(user.personalCode, "old@example.com", "+37251111111")

    then:
    1 * userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)

    then:
    1 * userRepository.findByEmail("old@example.com") >> Optional.of(user)
  }

  def "stores the phone number as submitted, also when it is blank"() {
    given:
    def user = sampleUser().email("old@example.com").phoneNumber("+37255555555").build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)
    userRepository.findByEmail("old@example.com") >> Optional.of(user)
    userRepository.save(user) >> user

    when:
    def updatedUser = service.changeContactDetails(user.personalCode, "old@example.com", "")

    then:
    updatedUser.phoneNumber == ""
    1 * contactChangeRecorder.recordSelfServiceChange(user, "old@example.com", "+37255555555")
  }

  def "changing contact details to an e-mail of another user throws a clear exception and records nothing"() {
    given:
    def user = sampleUser().build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)

    def userWithExistingEmail = simpleUser().build()
    def newEmail = userWithExistingEmail.email
    userRepository.findByEmail(newEmail) >> Optional.of(userWithExistingEmail)

    when:
    service.changeContactDetails(user.personalCode, newEmail, "555555")

    then:
    thrown(DuplicateEmailException)
    0 * userRepository.save(_)
    0 * contactChangeRecorder._
  }

  def "throws when changing contact details of a user that does not exist"() {
    given:
    userRepository.lockForUpdate(personalCodeSample) >> Optional.empty()
    userRepository.findByEmail("new@example.com") >> Optional.empty()

    when:
    service.changeContactDetails(personalCodeSample, "new@example.com", null)

    then:
    def exception = thrown(RuntimeException)
    exception.class == RuntimeException
    0 * contactChangeRecorder._
  }

  def "fills in missing email and phone number based on personal code"() {
    given:
    def user = sampleUser().email(null).phoneNumber(null).build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user
    def newEmail = "new@example.com"
    userRepository.findByEmail(newEmail) >> Optional.empty()

    when:
    def updatedUser = service.fillMissingContactDetails(user.personalCode, Optional.of(newEmail), "555555")

    then:
    0 * contactChangeRecorder._
    updatedUser.email == newEmail
    updatedUser.phoneNumber == "555555"
  }

  def "fills in only the phone number when no email is given"() {
    given:
    def user = sampleUser().email(null).phoneNumber(null).build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)
    userRepository.save(user) >> user

    when:
    def updatedUser = service.fillMissingContactDetails(user.personalCode, Optional.empty(), "555555")

    then:
    updatedUser.email == null
    updatedUser.phoneNumber == "555555"
  }

  def "filling in an e-mail of another user throws a clear exception"() {
    given:
    def user = sampleUser().email(null).phoneNumber(null).build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)

    def userWithExistingEmail = simpleUser().build()
    def newEmail = userWithExistingEmail.email
    userRepository.findByEmail(newEmail) >> Optional.of(userWithExistingEmail)

    when:
    service.fillMissingContactDetails(user.personalCode, Optional.of(newEmail), "555555")

    then:
    thrown(DuplicateEmailException)
  }

  def "leaves a user who already has contact details untouched when filling them in"() {
    given:
    def user = sampleUser().email(null).phoneNumber("+37255555555").build()
    userRepository.lockForUpdate(user.personalCode) >> Optional.of(user)

    when:
    def returnedUser = service.fillMissingContactDetails(user.personalCode, Optional.of("new@example.com"), "+37251111111")

    then:
    returnedUser == user
    user.email == null
    user.phoneNumber == "+37255555555"
    0 * userRepository.save(_)
    0 * userRepository.findByEmail(_)
    0 * contactChangeRecorder._
  }

  def "throws when filling in contact details of a user that does not exist"() {
    given:
    userRepository.lockForUpdate(personalCodeSample) >> Optional.empty()

    when:
    service.fillMissingContactDetails(personalCodeSample, Optional.empty(), null)

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
