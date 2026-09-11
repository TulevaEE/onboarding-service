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
    Provider.XTRACKERS_IE | IRELAND
    Provider.XTRACKERS_LU | LUXEMBOURG
    Provider.INVESCO     | IRELAND
    Provider.CCF         | IRELAND
    Provider.AMUNDI      | LUXEMBOURG
    Provider.BNP_PARIBAS | LUXEMBOURG
  }

  def "the two Xtrackers issuers are told apart by domicile, which is what the settlement calendar needs"() {
    expect:
    Provider.XTRACKERS_IE.domicile != Provider.XTRACKERS_LU.domicile
  }

  def "the legacy CCF label shares the domicile of ISHARES, both labelling the same Irish BlackRock fund"() {
    expect:
    Provider.CCF.domicile == Provider.ISHARES.domicile
  }
}
