package ee.tuleva.onboarding.kyb.screener

import ee.tuleva.onboarding.kyb.*
import spock.lang.Specification

import static ee.tuleva.onboarding.kyb.CompanyStatus.R
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER

class SoleBoardMemberIsOwnerScreenerSpec extends Specification {

  def screener = new SoleBoardMemberIsOwnerScreener()

  def "sole board member who is also shareholder and beneficial owner passes"() {
    given:
      def boardMember = new KybRelatedPerson("38501010001", true, true, true, 50.00)
      def otherPerson = new KybRelatedPerson("38501010002", false, true, true, 50.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [boardMember, otherPerson])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == SOLE_BOARD_MEMBER_IS_OWNER
      result.get().success()
  }

  def "sole board member who is not a shareholder fails"() {
    given:
      def boardMember = new KybRelatedPerson("38501010001", true, false, false, 0.00)
      def owner = new KybRelatedPerson("38501010002", false, true, true, 100.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [boardMember, owner])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == SOLE_BOARD_MEMBER_IS_OWNER
      !result.get().success()
  }

  def "sole board member who is shareholder but not beneficial owner fails"() {
    given:
      def boardMember = new KybRelatedPerson("38501010001", true, true, false, 50.00)
      def otherPerson = new KybRelatedPerson("38501010002", false, true, true, 50.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [boardMember, otherPerson])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == SOLE_BOARD_MEMBER_IS_OWNER
      !result.get().success()
  }

  def "does not apply when there are two board members"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 50.00)
      def person2 = new KybRelatedPerson("38501010002", true, true, true, 50.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person1, person2])

    when:
      def result = screener.screen(data)

    then:
      result.isEmpty()
  }

  def "does not apply when there is only one related person"() {
    given:
      def person = new KybRelatedPerson("38501010001", true, true, true, 100.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person])

    when:
      def result = screener.screen(data)

    then:
      result.isEmpty()
  }
}
