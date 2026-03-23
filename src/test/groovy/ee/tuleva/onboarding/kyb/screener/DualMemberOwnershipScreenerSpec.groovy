package ee.tuleva.onboarding.kyb.screener

import ee.tuleva.onboarding.kyb.*
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.kyb.CompanyStatus.R
import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP

class DualMemberOwnershipScreenerSpec extends Specification {

  def screener = new DualMemberOwnershipScreener()

  def "two board members who are both 100% shareholders and beneficial owners passes"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 50.00)
      def person2 = new KybRelatedPerson("38501010002", true, true, true, 50.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person1, person2])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == DUAL_MEMBER_OWNERSHIP
      result.get().success()
  }

  def "two board members with total ownership less than 100% fails"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 30.00)
      def person2 = new KybRelatedPerson("38501010002", true, true, true, 30.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person1, person2])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == DUAL_MEMBER_OWNERSHIP
      !result.get().success()
  }

  def "two board members where one is not shareholder fails"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 100.00)
      def person2 = new KybRelatedPerson("38501010002", true, false, false, 0.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person1, person2])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == DUAL_MEMBER_OWNERSHIP
      !result.get().success()
  }

  def "does not apply when only one board member out of two persons"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 50.00)
      def person2 = new KybRelatedPerson("38501010002", false, true, true, 50.00)
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
