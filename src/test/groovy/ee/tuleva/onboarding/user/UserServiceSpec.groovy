package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException
import ee.tuleva.onboarding.user.member.Member
import ee.tuleva.onboarding.user.member.MemberRepository
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember

class UserServiceSpec extends Specification {

  def userRepository = Mock(UserRepository)
  def memberRepository = Mock(MemberRepository)
  def service = new UserService(userRepository, memberRepository)

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

  def "can register a non member user as a member"() {
    given:
    def user = sampleUserNonMember().build()
    userRepository.findOne(user.id) >> user
    memberRepository.getMaxMemberNumber() >> 1000
    memberRepository.save(_ as Member) >> { Member member -> member }

    when:
    def member = service.registerAsMember(user.id)

    then:
    member.memberNumber == 1000 + 1
    member.user == user
  }

  def "trying to register a user who is already a member as a new member throws exception"() {
    given:
    def user = sampleUser()
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
    sampleUser()                  | true
    sampleUserNonMember().build() | false
  }

}
