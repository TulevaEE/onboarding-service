package ee.tuleva.onboarding.investment.portfolio

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.investment.calendar.Domicile.IRELAND
import static ee.tuleva.onboarding.investment.calendar.Domicile.LUXEMBOURG

class ProviderSpec extends Specification {

  @Unroll
  def "#provider is domiciled in #domicile"() {
    expect:
    provider.domicile == domicile

    where:
    provider             | domicile
    Provider.ISHARES     | IRELAND
    Provider.VANGUARD    | IRELAND
    Provider.XTRACKERS   | IRELAND
    Provider.INVESCO     | IRELAND
    Provider.CCF         | IRELAND
    Provider.AMUNDI      | LUXEMBOURG
    Provider.BNP_PARIBAS | LUXEMBOURG
  }

  def "the legacy CCF label shares the domicile of ISHARES, both labelling the same Irish BlackRock fund"() {
    expect:
    Provider.CCF.domicile == Provider.ISHARES.domicile
  }
}
