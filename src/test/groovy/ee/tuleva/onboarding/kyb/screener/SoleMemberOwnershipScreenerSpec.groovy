package ee.tuleva.onboarding.kyb.screener

import ee.tuleva.onboarding.kyb.*
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.kyb.CompanyStatus.R
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP

class SoleMemberOwnershipScreenerSpec extends Specification {

  def screener = new SoleMemberOwnershipScreener()

  @Unroll
  def "single person OÜ: boardMember=#board, shareholder=#share, beneficialOwner=#beneficial, ownership=#ownership => success=#expected"() {
    given:
      def person = new KybRelatedPerson("38501010001", board, share, beneficial, ownership)
      def data = new KybCompanyData("12345678", "38501010001", R, [person])

    when:
      def result = screener.screen(data)

    then:
      result.isPresent()
      result.get().type() == SOLE_MEMBER_OWNERSHIP
      result.get().success() == expected

    where:
      board | share | beneficial | ownership        || expected
      true  | true  | true       | 100.00           || true
      true  | true  | false      | 100.00           || false
      true  | false | true       | 0.00             || false
      true  | true  | true       | 50.00            || false
      false | true  | true       | 100.00           || false
  }

  def "does not apply when there are multiple related persons"() {
    given:
      def person1 = new KybRelatedPerson("38501010001", true, true, true, 50.00)
      def person2 = new KybRelatedPerson("38501010002", true, true, true, 50.00)
      def data = new KybCompanyData("12345678", "38501010001", R, [person1, person2])

    when:
      def result = screener.screen(data)

    then:
      result.isEmpty()
  }
}
