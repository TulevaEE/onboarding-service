package ee.tuleva.onboarding.fund

import spock.lang.Specification

import static ee.tuleva.onboarding.fund.FundFixture.exitRestricted3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.lhv3rdPillarFund
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund

class FundSpec extends Specification {
  def "is not exit restricted"() {
    given:
    def fund = tuleva2ndPillarStockFund()

    when:
    def isExitRestricted = fund.isExitRestricted()

    then:
    !isExitRestricted
  }

  def "is exit restricted"() {
    given:
    def fund = exitRestricted3rdPillarFund()

    when:
    def isExitRestricted = fund.isExitRestricted()

    then:
    isExitRestricted
  }

  def "is exit restricted by name"() {
    given:
    def fund = lhv3rdPillarFund()
    fund.nameEstonian += " (väljumine piiratud)"

    when:
    def isExitRestricted = fund.isExitRestricted()

    then:
    isExitRestricted
  }

  def "own fund follows the fund manager"() {
    expect:
    tuleva2ndPillarStockFund().isOwnFund()
    !lhv3rdPillarFund().isOwnFund()
  }

  def "compares by Estonian name"() {
    given:
    def early = tuleva2ndPillarStockFund()
    def late = tuleva2ndPillarStockFund()
    early.nameEstonian = "Aktsiafond"
    late.nameEstonian = "Võlakirjafond"

    expect:
    early.compareTo(late) < 0
    late.compareTo(early) > 0
    early.compareTo(early) == 0
  }
}
