package ee.tuleva.onboarding.investment.fees.instrument

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider
import ee.tuleva.onboarding.instrument.InstrumentReference
import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

class DayPriceReaderSpec extends Specification {

  static final String ISIN = "ZZ0000000001"
  static final LocalDate SUNDAY = LocalDate.of(2026, 11, 22)
  static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20)

  def priorityPriceProvider = Mock(PriorityPriceProvider)
  def instrumentReferenceService = Mock(InstrumentReferenceService)
  def reader = new DayPriceReader(priorityPriceProvider, instrumentReferenceService)

  def "a mutual fund takes its own price for the day without the NAV cutoff"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.of(instrument(null))
    priorityPriceProvider.resolve(ISIN, SUNDAY) >> Optional.of(feedPrice(FRIDAY))

    expect:
    reader.priceOn(ISIN, SUNDAY, holding(navPrice())) ==
        Optional.of(new InstrumentPrice(12.34, FRIDAY, "BLACKROCK"))
  }

  def "a mutual fund whose own price is over a week old takes the NAV report's price"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.of(instrument(null))
    priorityPriceProvider.resolve(ISIN, SUNDAY) >> Optional.of(feedPrice(SUNDAY.minusDays(8)))

    expect:
    reader.priceOn(ISIN, SUNDAY, holding(navPrice())) == Optional.of(navPrice())
  }

  def "an exchange traded fund takes the price the NAV used"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.of(instrument("ZZ01.XETRA"))

    when:
    def price = reader.priceOn(ISIN, SUNDAY, holding(navPrice()))

    then:
    price == Optional.of(navPrice())
    0 * priorityPriceProvider.resolve(_, _)
  }

  def "an exchange traded fund missing from the NAV report takes its own price"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.of(instrument("ZZ01.PA.EODHD"))
    priorityPriceProvider.resolve(ISIN, SUNDAY) >> Optional.of(feedPrice(FRIDAY))

    expect:
    reader.priceOn(ISIN, SUNDAY, holding(null)) ==
        Optional.of(new InstrumentPrice(12.34, FRIDAY, "BLACKROCK"))
  }

  def "an instrument missing from the reference is priced like a mutual fund"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.empty()
    priorityPriceProvider.resolve(ISIN, SUNDAY) >> Optional.of(feedPrice(FRIDAY))

    expect:
    reader.priceOn(ISIN, SUNDAY, holding(navPrice())) ==
        Optional.of(new InstrumentPrice(12.34, FRIDAY, "BLACKROCK"))
  }

  def "without either price there is none"() {
    given:
    instrumentReferenceService.findByIsin(ISIN) >> Optional.of(instrument(null))
    priorityPriceProvider.resolve(ISIN, SUNDAY) >> Optional.empty()

    expect:
    reader.priceOn(ISIN, SUNDAY, holding(null)) == Optional.empty()
  }

  private static InstrumentPrice navPrice() {
    new InstrumentPrice(12.00, FRIDAY, "NAV_REPORT")
  }

  private static Holding holding(InstrumentPrice navPrice) {
    new Holding(1000.000, FRIDAY, UnitsSource.NAV_REPORT, navPrice)
  }

  private static FundValue feedPrice(LocalDate date) {
    new FundValue("${ISIN}.BLACKROCK", date, 12.34, "BLACKROCK", Instant.parse("2026-11-21T08:00:00Z"))
  }

  private static InstrumentReference instrument(String eodhdTicker) {
    def instrument = new InstrumentReference()
    def field = InstrumentReference.getDeclaredField("eodhdTicker")
    field.setAccessible(true)
    field.set(instrument, eodhdTicker)
    instrument
  }
}
