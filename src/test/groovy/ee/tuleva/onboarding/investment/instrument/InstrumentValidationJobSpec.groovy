package ee.tuleva.onboarding.investment.instrument

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.fund.TulevaFund
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.ValidationFinding
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository
import ee.tuleva.onboarding.instrument.InstrumentDataFinding
import ee.tuleva.onboarding.instrument.ReferenceDataChange
import ee.tuleva.onboarding.instrument.ReferenceDataHistoryRepository
import ee.tuleva.onboarding.instrument.InstrumentReferenceService
import ee.tuleva.onboarding.notification.OperationsNotificationService
import ee.tuleva.onboarding.notification.email.EmailService
import ee.tuleva.onboarding.time.MutableClock
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT

class InstrumentValidationJobSpec extends Specification {

  InstrumentDataValidator validator = Mock()
  ModelPortfolioAllocationRepository allocationRepository = Mock()
  EmailService emailService = Mock()
  InstrumentReferenceService instrumentReferenceService = Stub()
  ReferenceDataHistoryRepository historyRepository = Mock()
  OperationsNotificationService notificationService = Mock()
  ReferenceDataChangeDescriber changeDescriber = new ReferenceDataChangeDescriber(new JsonMapper())
  MutableClock clock = new MutableClock(Instant.parse("2026-05-28T10:00:00Z"))

  InstrumentValidationJob job = new InstrumentValidationJob(
      validator, allocationRepository, emailService, instrumentReferenceService,
      historyRepository, changeDescriber, notificationService, clock)

  def today = LocalDate.of(2026, 5, 28)
  def effectiveDate = LocalDate.of(2026, 5, 26)

  def setup() {
    instrumentReferenceService.getLastRefreshedAt() >> { clock.instant() }
    historyRepository.unnotifiedChanges() >> []
  }

  def "alerts the investment channel when the instrument reference data has findings"() {
    given:
    noAllocations()
    instrumentReferenceService.dataFindings() >> [
        new InstrumentDataFinding.EodhdListedWithoutTicker("IE00TEST"),
        new InstrumentDataFinding.AmbiguousLookupKey("shortTicker", "DUP", ["IE00AAA", "IE00BBB"]),
    ]

    when:
    job.run()

    then:
    1 * notificationService.sendMessage(
        { it.contains("INSTRUMENT REFERENCE DATA BROKEN") && it.contains("IE00TEST") && it.contains("DUP") },
        INVESTMENT)
  }

  def "suppresses an unchanged instrument reference data alert on the same day"() {
    given:
    noAllocations()
    instrumentReferenceService.dataFindings() >> [
        new InstrumentDataFinding.EodhdListedWithoutTicker("IE00TEST")
    ]

    when:
    job.run()
    job.run()

    then:
    1 * notificationService.sendMessage(_ as String, INVESTMENT)
  }

  def "re-alerts the next day even when the findings have not changed"() {
    given:
    noAllocations()
    instrumentReferenceService.dataFindings() >> [
        new InstrumentDataFinding.EodhdListedWithoutTicker("IE00TEST")
    ]

    when:
    job.run()
    clock.tick(1, ChronoUnit.DAYS)
    job.run()

    then:
    2 * notificationService.sendMessage(_ as String, INVESTMENT)
  }

  def "re-alerts on the same day when the findings change"() {
    given:
    noAllocations()
    instrumentReferenceService.dataFindings() >>> [
        [new InstrumentDataFinding.EodhdListedWithoutTicker("IE00TEST")],
        [new InstrumentDataFinding.EodhdListedWithoutTicker("IE00OTHER")],
    ]

    when:
    job.run()
    job.run()

    then:
    2 * notificationService.sendMessage(_ as String, INVESTMENT)
  }

  def "sends a clearing notice once the reference data problems are gone"() {
    given:
    noAllocations()
    instrumentReferenceService.dataFindings() >>> [
        [new InstrumentDataFinding.EodhdListedWithoutTicker("IE00TEST")],
        [],
        [],
    ]

    when:
    job.run()
    job.run()
    job.run()

    then:
    1 * notificationService.sendMessage({ it.contains("INSTRUMENT REFERENCE DATA BROKEN") }, INVESTMENT)
    1 * notificationService.sendMessage({ it.contains("INSTRUMENT REFERENCE DATA OK") }, INVESTMENT)
  }

  def "sends email when FAIL findings exist"() {
    given:
    def allocation = allocation(effectiveDate)
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "Missing instrument_reference for IE00TEST")
    ]

    when:
    job.run()

    then:
    (1.._) * emailService.sendSystemEmail(_ as MandrillMessage) >> true
  }

  def "does not send email when only WARNING findings"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation(effectiveDate)]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, effectiveDate) >> [
        new ValidationFinding(Severity.WARNING, "Ticker mismatch")
    ]

    when:
    job.run()

    then:
    0 * emailService.sendSystemEmail(_)
  }

  def "does not send email when no findings"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation(effectiveDate)]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, _) >> []

    when:
    job.run()

    then:
    0 * emailService.sendSystemEmail(_)
  }

  def "skips funds with no allocations"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []

    when:
    job.run()

    then:
    0 * validator.validate(_, _)
    0 * emailService.sendSystemEmail(_)
  }

  def "alert email contains fund code and finding details"() {
    given:
    allocationRepository.findLatestByFundAsOf(TUK75, today) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(TUK75, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "IE00TEST not in instrument_reference")
    ]

    when:
    job.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[FAIL] Instrument validation findings" &&
          msg.text.contains("TUK75") &&
          msg.text.contains("IE00TEST not in instrument_reference") &&
          msg.fromEmail == "funds@tuleva.ee" &&
          msg.to[0].email == "funds@tuleva.ee"
    }) >> true
  }

  def "logs error when email fails to send"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation(effectiveDate)]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "test failure")
    ]
    emailService.sendSystemEmail(_) >> false

    when:
    job.run()

    then:
    noExceptionThrown()
  }

  def "validates upcoming model portfolio versions so price-history readiness is checked before go-live"() {
    given:
    def futureDate = LocalDate.of(2026, 6, 15)
    allocationRepository.findLatestByFundAsOf(TUK75, today) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(TUK75, today) >> [futureDate]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(TUK75, effectiveDate) >> []
    validator.validate(TUK75, futureDate) >> [
        new ValidationFinding(Severity.FAIL, "ISIN IE00NEW has only 0 business days of prices (need 2) — not ready for model portfolio")
    ]

    when:
    job.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.text.contains("2026-06-15") &&
          msg.text.contains("not ready for model portfolio")
    }) >> true
  }

  def "alerts when the instrument cache has not refreshed for over three hours"() {
    given:
    def staleService = Stub(InstrumentReferenceService)
    staleService.getLastRefreshedAt() >> { clock.instant().minus(4, ChronoUnit.HOURS) }
    def staleJob = new InstrumentValidationJob(
        validator, allocationRepository, emailService, staleService,
        historyRepository, changeDescriber, notificationService, clock)
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []

    when:
    staleJob.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[STALE] Instrument reference cache"
    }) >> true
  }

  def "does not alert when the instrument cache refreshed within three hours"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []

    when:
    job.run()

    then:
    0 * emailService.sendSystemEmail(_)
  }

  def "sends only one alert a day while the finding set is unchanged"() {
    given:
    allocationRepository.findLatestByFundAsOf(TUK75, _ as LocalDate) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []
    validator.validate(TUK75, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "stable failure")
    ]

    when:
    job.run()
    clock.tick(1, ChronoUnit.HOURS)
    job.run()

    then:
    1 * emailService.sendSystemEmail(_ as MandrillMessage) >> true
  }

  def "alerts again the next day for an unchanged finding set"() {
    given:
    allocationRepository.findLatestByFundAsOf(TUK75, _ as LocalDate) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []
    validator.validate(TUK75, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "stable failure")
    ]

    when:
    job.run()
    clock.tick(1, ChronoUnit.DAYS)
    job.run()

    then:
    2 * emailService.sendSystemEmail(_ as MandrillMessage) >> true
  }

  def "alerts immediately when the finding set changes"() {
    given:
    allocationRepository.findLatestByFundAsOf(TUK75, _ as LocalDate) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []
    validator.validate(TUK75, effectiveDate) >>> [
        [new ValidationFinding(Severity.FAIL, "first failure")],
        [new ValidationFinding(Severity.FAIL, "second failure")]
    ]

    when:
    job.run()
    clock.tick(1, ChronoUnit.HOURS)
    job.run()

    then:
    2 * emailService.sendSystemEmail(_ as MandrillMessage) >> true
  }

  def "sends a clearing notice once the findings disappear"() {
    given:
    allocationRepository.findLatestByFundAsOf(TUK75, _ as LocalDate) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []
    validator.validate(TUK75, effectiveDate) >>> [
        [new ValidationFinding(Severity.FAIL, "transient failure")],
        [],
        []
    ]

    when:
    job.run()
    clock.tick(1, ChronoUnit.HOURS)
    job.run()
    clock.tick(1, ChronoUnit.HOURS)
    job.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[FAIL] Instrument validation findings"
    }) >> true

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[OK] Instrument validation findings cleared"
    }) >> true
  }

  def "mails a detected instrument reference change set and stamps it notified"() {
    given:
    noAllocations()
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >> [change(7L, "UPDATE",
        '{"isin": "IE00B4L5Y983", "benchmark_category": "EQUITY_DM", "active": true}',
        '{"isin": "IE00B4L5Y983", "benchmark_category": "EQUITY_EM", "active": true}')]

    when:
    jobWith(changedRepository).run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[CHANGED] Instrument reference data" &&
          msg.fromEmail == "funds@tuleva.ee" &&
          msg.to[0].email == "funds@tuleva.ee" &&
          msg.text.contains("UPDATE instrument_reference IE00B4L5Y983 by ops-console") &&
          msg.text.contains("benchmark_category: EQUITY_DM -> EQUITY_EM") &&
          !msg.text.contains("active")
    }) >> true

    then:
    1 * changedRepository.markNotified([7L])
  }

  def "mails a re-pointed benchmark proxy the same way it mails an instrument change"() {
    given:
    noAllocations()
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >> [proxyChange(9L,
        '{"benchmark_category": "BOND_GLOBAL", "etf_proxy_isin": "IE00BDBRDM35"}',
        '{"benchmark_category": "BOND_GLOBAL", "etf_proxy_isin": "LU1708330318"}')]

    when:
    jobWith(changedRepository).run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[CHANGED] Instrument reference data" &&
          msg.to[0].email == "funds@tuleva.ee" &&
          msg.text.contains("UPDATE benchmark_category_proxy BOND_GLOBAL by ops-console") &&
          msg.text.contains("etf_proxy_isin: IE00BDBRDM35 -> LU1708330318")
    }) >> true

    then:
    1 * changedRepository.markNotified([9L])
  }

  def "sends a single mail for the whole detected change set"() {
    given:
    noAllocations()
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >> [
        change(7L, "UPDATE", '{"isin": "IE00B4L5Y983", "active": true}', '{"isin": "IE00B4L5Y983", "active": false}'),
        change(8L, "INSERT", null, '{"isin": "IE00NEW00000", "display_name": "New ETF"}'),
        proxyChange(9L, '{"etf_proxy_isin": "IE00BDBRDM35"}', '{"etf_proxy_isin": "LU1708330318"}'),
    ]

    when:
    jobWith(changedRepository).run()

    then:
    1 * emailService.sendSystemEmail(_ as MandrillMessage) >> true
    1 * changedRepository.markNotified([7L, 8L, 9L])
  }

  def "does not mail or stamp anything when no unnotified changes exist"() {
    given:
    noAllocations()

    when:
    job.run()

    then:
    0 * emailService.sendSystemEmail(_)
    0 * historyRepository.markNotified(_)
  }

  def "leaves the change unstamped when the mail fails, so the next run retries it"() {
    given:
    noAllocations()
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >> [change(7L, "UPDATE", '{"active": true}', '{"active": false}')]
    emailService.sendSystemEmail(_) >> false

    when:
    jobWith(changedRepository).run()

    then:
    0 * changedRepository.markNotified(_)
  }

  def "leaves an undescribable change unstamped and unmailed, and reports it once it can be described"() {
    given:
    noAllocations()
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >>> [
        [change(7L, "UPDATE", '{"active":', '{"active": false}')],
        [change(7L, "UPDATE", '{"active": true}', '{"active": false}')],
    ]
    def jobUnderTest = jobWith(changedRepository)

    when:
    jobUnderTest.run()

    then:
    0 * emailService.sendSystemEmail(_)
    0 * changedRepository.markNotified(_)

    when:
    jobUnderTest.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[CHANGED] Instrument reference data" &&
          msg.text.contains("active: true -> false")
    }) >> true
    1 * changedRepository.markNotified([7L])
  }

  def "validates the funds even when the stale cache check blows up"() {
    given:
    def brokenService = Stub(InstrumentReferenceService)
    brokenService.getLastRefreshedAt() >> { throw new IllegalStateException("cache has never been loaded") }
    def brokenJob = new InstrumentValidationJob(
        validator, allocationRepository, emailService, brokenService,
        historyRepository, changeDescriber, notificationService, clock)
    allocationRepository.findLatestByFundAsOf(TUK75, today) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(TUK75, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "IE00TEST not in instrument_reference")
    ]

    when:
    brokenJob.run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[FAIL] Instrument validation findings"
    }) >> true
  }

  def "validates the funds even when the change notification blows up"() {
    given:
    def changedRepository = Mock(ReferenceDataHistoryRepository)
    changedRepository.unnotifiedChanges() >> { throw new IllegalStateException("history unreadable") }
    allocationRepository.findLatestByFundAsOf(TUK75, today) >> [allocation(effectiveDate)]
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(TUK75, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "IE00TEST not in instrument_reference")
    ]

    when:
    jobWith(changedRepository).run()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.subject == "[FAIL] Instrument validation findings"
    }) >> true
  }

  private void noAllocations() {
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, _ as LocalDate) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, _ as LocalDate) >> []
  }

  private InstrumentValidationJob jobWith(ReferenceDataHistoryRepository repository) {
    new InstrumentValidationJob(
        validator, allocationRepository, emailService, instrumentReferenceService,
        repository, changeDescriber, notificationService, clock)
  }

  private ReferenceDataChange change(Long id, String operation, String oldValues, String newValues) {
    new ReferenceDataChange(id, "instrument_reference", "IE00B4L5Y983", operation, "ops-console",
        clock.instant(), oldValues, newValues)
  }

  private ReferenceDataChange proxyChange(Long id, String oldValues, String newValues) {
    new ReferenceDataChange(id, "benchmark_category_proxy", "BOND_GLOBAL", "UPDATE", "ops-console",
        clock.instant(), oldValues, newValues)
  }

  private ModelPortfolioAllocation allocation(LocalDate date) {
    ModelPortfolioAllocation.builder()
        .effectiveDate(date).fund(TUK75).isin("IE00TEST").weight(1.0).build()
  }
}
