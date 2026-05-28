package ee.tuleva.onboarding.investment.instrument

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider
import ee.tuleva.onboarding.deadline.PublicHolidays
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository
import ee.tuleva.onboarding.investment.portfolio.PositionLimit
import ee.tuleva.onboarding.investment.portfolio.PositionLimitRepository
import ee.tuleva.onboarding.investment.portfolio.Provider
import ee.tuleva.onboarding.investment.portfolio.ProviderLimit
import ee.tuleva.onboarding.investment.portfolio.ProviderLimitRepository
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75
import static ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity.FAIL
import static ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity.WARNING

class InstrumentDataValidatorSpec extends Specification {

  InstrumentReferenceService instrumentReferenceService = Mock()
  ModelPortfolioAllocationRepository allocationRepository = Mock()
  PositionLimitRepository positionLimitRepository = Mock()
  ProviderLimitRepository providerLimitRepository = Mock()
  FundValueProvider fundValueProvider = Mock()
  PublicHolidays publicHolidays = Mock()
  Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

  InstrumentDataValidator validator = new InstrumentDataValidator(
      instrumentReferenceService, allocationRepository, positionLimitRepository,
      providerLimitRepository, fundValueProvider, publicHolidays, clock)

  def effectiveDate = LocalDate.of(2026, 5, 28)
  def isin1 = "IE00B4L5Y983"
  def isin2 = "IE00B4L5YC18"

  def "returns empty findings when no allocations"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> []

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.isEmpty()
  }

  def "FAIL when ISIN not in instrument_reference"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.empty()
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains(isin1) && it.message().contains("not in instrument_reference") }
  }

  def "FAIL when weights do not sum to 1.0"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [
        allocation(isin1, 0.6), allocation(isin2, 0.3)
    ]
    instrumentReferenceService.findByIsin(_) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1), positionLimit(isin2)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains("Weight sum") }
  }

  def "no weight finding when weights sum to 1.0"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [
        allocation(isin1, 0.6), allocation(isin2, 0.4)
    ]
    instrumentReferenceService.findByIsin(_) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1), positionLimit(isin2)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.every { !it.message().contains("Weight sum") }
  }

  def "FAIL when position limit missing for ISIN"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> []
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains("No position limit") }
  }

  def "FAIL when provider limit missing for TKF100"() {
    given:
    def alloc = allocation(isin1, 1.0, Provider.ISHARES)
    allocationRepository.findByFundAndEffectiveDate(TKF100, effectiveDate) >> [alloc]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TKF100, effectiveDate) >> [positionLimit(isin1)]
    providerLimitRepository.findLatestByFundAsOf(TKF100, effectiveDate) >> []
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TKF100, effectiveDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains("No provider limit") }
  }

  def "skips provider limit check for non-TKF100 funds"() {
    given:
    def alloc = allocation(isin1, 1.0, Provider.ISHARES)
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [alloc]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    0 * providerLimitRepository.findLatestByFundAsOf(TUK75, _)
    findings.every { !it.message().contains("No provider limit") }
  }

  def "WARNING when benchmark proxy missing for category"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, benchmarkCategory: "EQUITY_DM", eodhdTicker: "EUNL.XETRA"))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy("EQUITY_DM", true) >> Optional.empty()

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == WARNING && it.message().contains("No benchmark proxy") }
  }

  def "skips benchmark proxy check when category is null"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, benchmarkCategory: null))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.every { !it.message().contains("No benchmark proxy") }
  }

  def "FAIL when instrument is inactive"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: false))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains("active=false") }
  }

  def "WARNING when ticker mismatch between allocation and instrument_reference"() {
    given:
    def alloc = ModelPortfolioAllocation.builder()
        .effectiveDate(effectiveDate).fund(TUK75).isin(isin1).weight(1.0).ticker("WRONG.DE").build()
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [alloc]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, yahooTicker: "EUNL.DE"))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.any { it.severity() == WARNING && it.message().contains("Ticker mismatch") }
  }

  def "no ticker finding when tickers match"() {
    given:
    def alloc = ModelPortfolioAllocation.builder()
        .effectiveDate(effectiveDate).fund(TUK75).isin(isin1).weight(1.0).ticker("EUNL.DE").build()
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [alloc]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, yahooTicker: "EUNL.DE"))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.every { !it.message().contains("Ticker mismatch") }
  }

  def "skips ticker check when allocation ticker is null"() {
    given:
    def alloc = ModelPortfolioAllocation.builder()
        .effectiveDate(effectiveDate).fund(TUK75).isin(isin1).weight(1.0).ticker(null).build()
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [alloc]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.every { !it.message().contains("Ticker mismatch") }
  }

  def "FAIL when future allocation has insufficient price history"() {
    given:
    def futureDate = LocalDate.of(2026, 6, 1)
    allocationRepository.findByFundAndEffectiveDate(TUK75, futureDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, eodhdTicker: "EUNL.XETRA"))
    positionLimitRepository.findLatestByFundAsOf(TUK75, futureDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")
    publicHolidays.previousWorkingDay(_) >> { LocalDate d -> d.minusDays(1) }
    fundValueProvider.getLatestValue(_, _) >> Optional.empty()

    when:
    def findings = validator.validate(TUK75, futureDate)

    then:
    findings.any { it.severity() == FAIL && it.message().contains("business days of prices") }
  }

  def "no price history check when effective date is today or past"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    0 * fundValueProvider.getLatestValue(_, _)
    findings.every { !it.message().contains("business days of prices") }
  }

  def "no price history finding when sufficient prices exist"() {
    given:
    def futureDate = LocalDate.of(2026, 6, 1)
    allocationRepository.findByFundAndEffectiveDate(TUK75, futureDate) >> [allocation(isin1, 1.0)]
    instrumentReferenceService.findByIsin(isin1) >> Optional.of(instrument(active: true, eodhdTicker: "EUNL.XETRA"))
    positionLimitRepository.findLatestByFundAsOf(TUK75, futureDate) >> [positionLimit(isin1)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")
    publicHolidays.previousWorkingDay(_) >> { LocalDate d -> d.minusDays(1) }
    fundValueProvider.getLatestValue(_ as String, _ as LocalDate) >> Optional.of(Mock(FundValue))

    when:
    def findings = validator.validate(TUK75, futureDate)

    then:
    findings.every { !it.message().contains("business days of prices") }
  }

  def "all checks pass with valid data"() {
    given:
    allocationRepository.findByFundAndEffectiveDate(TUK75, effectiveDate) >> [
        allocation(isin1, 0.6), allocation(isin2, 0.4)
    ]
    instrumentReferenceService.findByIsin(_) >> Optional.of(instrument(active: true))
    positionLimitRepository.findLatestByFundAsOf(TUK75, effectiveDate) >> [positionLimit(isin1), positionLimit(isin2)]
    instrumentReferenceService.resolveBenchmarkProxy(_, _) >> Optional.of("proxy")

    when:
    def findings = validator.validate(TUK75, effectiveDate)

    then:
    findings.isEmpty()
  }

  private ModelPortfolioAllocation allocation(String isin, BigDecimal weight, Provider provider = null) {
    ModelPortfolioAllocation.builder()
        .effectiveDate(effectiveDate).fund(TUK75).isin(isin).weight(weight).provider(provider).build()
  }

  private PositionLimit positionLimit(String isin) {
    PositionLimit.builder()
        .effectiveDate(effectiveDate).fund(TUK75).isin(isin)
        .softLimitPercent(0.15).hardLimitPercent(0.20).build()
  }

  private InstrumentReference instrument(Map props = [:]) {
    def inst = new InstrumentReference()
    def fields = InstrumentReference.getDeclaredFields()
    setField(inst, "active", props.containsKey("active") ? props.active : true)
    if (props.benchmarkCategory) setField(inst, "benchmarkCategory", props.benchmarkCategory)
    if (props.eodhdTicker) setField(inst, "eodhdTicker", props.eodhdTicker)
    if (props.yahooTicker) setField(inst, "yahooTicker", props.yahooTicker)
    if (props.isin) setField(inst, "isin", props.isin)
    return inst
  }

  private static void setField(Object obj, String fieldName, Object value) {
    def field = InstrumentReference.getDeclaredField(fieldName)
    field.setAccessible(true)
    field.set(obj, value)
  }
}
