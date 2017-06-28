package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.notification.mailchimp.MailChimpService
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException
import ee.tuleva.onboarding.user.member.MemberRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def memberRepository = Mock(MemberRepository)
  def mailChimpService = Mock(MailChimpService)
  def service = new UserService(userRepository, memberRepository, mailChimpService)

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
    userRepository.findByPersonalCode(user.personalCode) >> user
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
    userRepository.findOne(user.id) >> user

    when:
    def result = service.isAMember(user.id)

    then:
    result == isAMember

    where:
    user                          | isAMember
    sampleUser().build()          | true
    sampleUserNonMember().build() | false
  }

  def "updating a user also updates it in Mailchimp"() {
    given:
    def user = sampleUser().build()
    userRepository.findByPersonalCode(user.personalCode) >> user
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

}
