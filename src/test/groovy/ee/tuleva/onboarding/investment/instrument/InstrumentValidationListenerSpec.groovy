package ee.tuleva.onboarding.investment.instrument

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import ee.tuleva.onboarding.fund.TulevaFund
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.Severity
import ee.tuleva.onboarding.investment.instrument.InstrumentDataValidator.ValidationFinding
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75

class InstrumentValidationListenerSpec extends Specification {

  InstrumentDataValidator validator = Mock()
  ModelPortfolioAllocationRepository allocationRepository = Mock()
  EmailService emailService = Mock()
  Clock clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.of("Europe/Tallinn"))

  InstrumentValidationListener listener = new InstrumentValidationListener(
      validator, allocationRepository, emailService, clock)

  def today = LocalDate.of(2026, 5, 28)
  def effectiveDate = LocalDate.of(2026, 5, 26)

  def "sends email when FAIL findings exist"() {
    given:
    def allocation = allocation(effectiveDate)
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, effectiveDate) >> [
        new ValidationFinding(Severity.FAIL, "Missing instrument_reference for IE00TEST")
    ]

    when:
    listener.onCacheRefreshed()

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
    listener.onCacheRefreshed()

    then:
    0 * emailService.sendSystemEmail(_)
  }

  def "does not send email when no findings"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> [allocation(effectiveDate)]
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []
    validator.validate(_ as TulevaFund, _) >> []

    when:
    listener.onCacheRefreshed()

    then:
    0 * emailService.sendSystemEmail(_)
  }

  def "skips funds with no allocations"() {
    given:
    allocationRepository.findLatestByFundAsOf(_ as TulevaFund, today) >> []
    allocationRepository.findFutureEffectiveDates(_ as TulevaFund, today) >> []

    when:
    listener.onCacheRefreshed()

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
    listener.onCacheRefreshed()

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
    listener.onCacheRefreshed()

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
    listener.onCacheRefreshed()

    then:
    1 * emailService.sendSystemEmail({ MandrillMessage msg ->
      msg.text.contains("2026-06-15") &&
          msg.text.contains("not ready for model portfolio")
    }) >> true
  }

  private ModelPortfolioAllocation allocation(LocalDate date) {
    ModelPortfolioAllocation.builder()
        .effectiveDate(date).fund(TUK75).isin("IE00TEST").weight(1.0).build()
  }
}
