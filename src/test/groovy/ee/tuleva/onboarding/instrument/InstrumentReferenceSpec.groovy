package ee.tuleva.onboarding.instrument

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalTime
import java.time.ZoneId

class InstrumentReferenceSpec extends Specification {

  @Unroll
  def "isExchangeTraded returns #expected for eodhd ticker #eodhdTicker"() {
    expect:
    instrument(eodhdTicker: eodhdTicker).isExchangeTraded() == expected

    where:
    eodhdTicker      | expected
    "EUNL.XETRA"    | true
    "WLXU.PA.EODHD" | true
    "0P00.EUFUND"    | false
    null             | false
  }

  @Unroll
  def "isListedOnEodhd returns #expected when eodhdListed is #eodhdListed"() {
    expect:
    instrument(eodhdListed: eodhdListed).isListedOnEodhd() == expected

    where:
    eodhdListed | expected
    true        | true
    false       | false
    null        | true
  }

  def "getXetraStorageKey returns ISIN.XETR for Xetra instruments"() {
    expect:
    instrument(isin: "IE00B4L5Y983", eodhdTicker: "EUNL.XETRA").getXetraStorageKey() ==
        Optional.of("IE00B4L5Y983.XETR")
  }

  def "getXetraStorageKey returns empty for non-Xetra instruments"() {
    expect:
    instrument(eodhdTicker: "WLXU.PA.EODHD").getXetraStorageKey() == Optional.empty()
    instrument(eodhdTicker: null).getXetraStorageKey() == Optional.empty()
  }

  def "getEuronextParisStorageKey returns ISIN.XPAR for Euronext Paris instruments"() {
    expect:
    instrument(isin: "FR0013209921", eodhdTicker: "WLXU.PA.EODHD").getEuronextParisStorageKey() ==
        Optional.of("FR0013209921.XPAR")
  }

  def "getEuronextParisStorageKey returns empty for non-Euronext instruments"() {
    expect:
    instrument(eodhdTicker: "EUNL.XETRA").getEuronextParisStorageKey() == Optional.empty()
    instrument(eodhdTicker: null).getEuronextParisStorageKey() == Optional.empty()
  }

  def "getBlackrockStorageKey returns ISIN.BLACKROCK when product ID exists"() {
    expect:
    instrument(isin: "LU0290358497", blackrockProductId: "283108").getBlackrockStorageKey() ==
        Optional.of("LU0290358497.BLACKROCK")
  }

  def "getBlackrockStorageKey returns empty when no product ID"() {
    expect:
    instrument(blackrockProductId: null).getBlackrockStorageKey() == Optional.empty()
  }

  def "getMorningstarStorageKey returns ISIN.MORNINGSTAR when morningstar ID exists"() {
    expect:
    instrument(isin: "IE00BKM4GZ66", morningstarId: "F00000Q7RC").getMorningstarStorageKey() ==
        Optional.of("IE00BKM4GZ66.MORNINGSTAR")
  }

  def "getMorningstarStorageKey returns empty when no morningstar ID"() {
    expect:
    instrument(morningstarId: null).getMorningstarStorageKey() == Optional.empty()
  }

  def "getEffectiveDisplayName returns sebPositionName when set"() {
    expect:
    instrument(displayName: "Display", sebPositionName: "SEB Name").getEffectiveDisplayName() == "SEB Name"
  }

  def "getEffectiveDisplayName returns displayName when sebPositionName is null"() {
    expect:
    instrument(displayName: "Display", sebPositionName: null).getEffectiveDisplayName() == "Display"
  }

  def "settlementTerms returns the terms when all three columns are set"() {
    expect:
    instrument(
        settlementCutoffTime: LocalTime.of(9, 30),
        settlementCutoffZone: "Europe/Tallinn",
        settlementDaysFromAcceptance: 3
    ).settlementTerms() == Optional.of(
        new SettlementTerms(LocalTime.of(9, 30), ZoneId.of("Europe/Tallinn"), 3))
  }

  @Unroll
  def "settlementTerms is empty when #missing is null"() {
    expect:
    instrument(props).settlementTerms() == Optional.empty()

    where:
    missing            | props
    "cutoff time"      | [settlementCutoffZone: "Europe/Tallinn", settlementDaysFromAcceptance: 3]
    "cutoff zone"      | [settlementCutoffTime: LocalTime.of(9, 30), settlementDaysFromAcceptance: 3]
    "days"             | [settlementCutoffTime: LocalTime.of(9, 30), settlementCutoffZone: "Europe/Tallinn"]
    "all three"        | [:]
  }

  private static InstrumentReference instrument(Map props = [:]) {
    def inst = new InstrumentReference()
    props.each { key, value ->
      if (value != null) {
        def field = InstrumentReference.getDeclaredField(key as String)
        field.setAccessible(true)
        field.set(inst, value)
      }
    }
    return inst
  }
}
