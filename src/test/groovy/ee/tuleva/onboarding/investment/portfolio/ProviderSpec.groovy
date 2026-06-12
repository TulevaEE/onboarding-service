package ee.tuleva.onboarding.investment.portfolio

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.investment.calendar.Domicile.FRANCE
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
    Provider.CCF         | FRANCE
    Provider.AMUNDI      | LUXEMBOURG
    Provider.BNP_PARIBAS | LUXEMBOURG
  }
}
