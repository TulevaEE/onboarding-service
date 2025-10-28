package ee.tuleva.onboarding.capital

import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventRepository
import ee.tuleva.onboarding.user.member.Member
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventFixture.memberCapitalEventFixture
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*
import static ee.tuleva.onboarding.capital.event.organisation.OrganisationCapitalEventType.INVESTMENT_RETURN
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture
import static java.math.RoundingMode.HALF_DOWN

class CapitalServiceSpec extends Specification {
  MemberCapitalEventRepository memberCapitalEventRepository = Mock()
  AggregatedCapitalEventRepository aggregatedCapitalEventRepository = Mock()
  CapitalService service = new CapitalService(memberCapitalEventRepository, aggregatedCapitalEventRepository)

  def "GetCapitalStatement"() {
    given:
    Member member = memberFixture().build()
    def event1 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(1000.00)
        .ownershipUnitAmount(1000.00).build()
    def event2 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(0.123456)
        .ownershipUnitAmount(0.1).build()
    def event3 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(2000.00)
        .ownershipUnitAmount(1900.0).build()
    def event4 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(0.234567)
        .ownershipUnitAmount(0.2).build()
    def event5 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(3000.00)
        .ownershipUnitAmount(2900.0).build()
    def event6 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(0.345678)
        .ownershipUnitAmount(0.3).build()
    def event7 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(4000.00)
        .ownershipUnitAmount(3900.0).build()
    def event8 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(0.456789)
        .ownershipUnitAmount(0.4).build()

    def events = [event1, event2, event3, event4, event5, event6, event7, event8]
    memberCapitalEventRepository.findAllByMemberId(member.id) >> events

    def ownershipUnitPrice = 1.567890
    aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
        getAggregatedCapitalEvent(ownershipUnitPrice)

    when:
    CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

    then:
    capitalStatement.capitalPayment == 1000.12
    capitalStatement.membershipBonus == 2000.23
    capitalStatement.unvestedWorkCompensation == 3000.35
    capitalStatement.workCompensation == 4000.46
    capitalStatement.profit == 5208.94
    capitalStatement.total == 1000.12 + 2000.23 + 3000.35 + 4000.46 + 5208.94
    capitalStatement.currency == EUR
  }

  def "works with no capital"() {
    given:
    Member member = memberFixture().build()
    memberCapitalEventRepository.findAllByMemberId(member.id) >> []
    aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >> null

    when:
    CapitalStatement capitalStatement = service.getCapitalStatement(member.id)

    then:
    capitalStatement.capitalPayment == 0
    capitalStatement.membershipBonus == 0
    capitalStatement.unvestedWorkCompensation == 0
    capitalStatement.workCompensation == 0
    capitalStatement.profit == 0
    capitalStatement.total == 0
    capitalStatement.currency == EUR
  }

  def "can get a list of capital rows"() {
    given:
    Member member = memberFixture().build()
    def event1 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(1000.00)
        .ownershipUnitAmount(1000.00).build()
    def event2 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(0.123456)
        .ownershipUnitAmount(0.1).build()
    def event3 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(2000.00)
        .ownershipUnitAmount(1900.0).build()
    def event4 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(0.234567)
        .ownershipUnitAmount(0.2).build()
    def event5 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(3000.00)
        .ownershipUnitAmount(2900.0).build()
    def event6 = memberCapitalEventFixture(member).type(UNVESTED_WORK_COMPENSATION).fiatValue(0.345678)
        .ownershipUnitAmount(0.3).build()
    def event7 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(4000.00)
        .ownershipUnitAmount(3900.0).build()
    def event8 = memberCapitalEventFixture(member).type(WORK_COMPENSATION).fiatValue(0.456789)
        .ownershipUnitAmount(0.4).build()

    def events = [event1, event2, event3, event4, event5, event6, event7, event8]
    memberCapitalEventRepository.findAllByMemberId(member.id) >> events

    def ownershipUnitPrice = 1.567890

    aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
        getAggregatedCapitalEvent(ownershipUnitPrice)

    when:
    List<CapitalRow> capitalRows = service.getCapitalRows(member.id)

    then:
    with(capitalRows.find({ it.type() == MEMBERSHIP_BONUS })) {
      contributions() == 2000.23
      profit() == 979.07
      getValue() == CapitalCalculations.calculateCapitalValue(new BigDecimal("1900.20"), ownershipUnitPrice)
      unitCount() == 1900.00 + 0.20
      unitPrice() == ownershipUnitPrice
      currency() == EUR
    }
    with(capitalRows.find({ it.type() == CAPITAL_PAYMENT })) {
      contributions() == 1000.12
      profit() == 567.92
      getValue() == CapitalCalculations.calculateCapitalValue(new BigDecimal("1000.10"), ownershipUnitPrice)
      unitCount() == 1000 + 0.10
      unitPrice() == ownershipUnitPrice
      currency() == EUR
    }
    with(capitalRows.find({ it.type() == WORK_COMPENSATION })) {
      contributions() == 4000.46
      profit() == 2114.94
      getValue() == CapitalCalculations.calculateCapitalValue(new BigDecimal("3900.40"), ownershipUnitPrice)
      unitPrice() == ownershipUnitPrice
      unitCount() == 3900.0 + 0.40
      currency() == EUR
    }
    with(capitalRows.find({ it.type() == UNVESTED_WORK_COMPENSATION })) {
      contributions() == 3000.35
      profit() == 1547.00
      getValue() == CapitalCalculations.calculateCapitalValue(new BigDecimal("2900.30"), ownershipUnitPrice)
      unitPrice() == ownershipUnitPrice
      unitCount() == 2900.0 + 0.30
      currency() == EUR
    }
  }

  def "can get capital events"() {
    given:
    Member member = memberFixture().build()
    def event1 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(1000.00).build()
    def event2 = memberCapitalEventFixture(member).type(CAPITAL_PAYMENT).fiatValue(0.123456).build()
    def event3 = memberCapitalEventFixture(member).type(MEMBERSHIP_BONUS).fiatValue(2000.00).build()
    memberCapitalEventRepository.findAllByMemberId(member.id) >> [event1, event2, event3]

    when:
    List<ApiCapitalEvent> capitalEvents = service.getCapitalEvents(member.id)

    then:
    capitalEvents == [
        new ApiCapitalEvent(event1.accountingDate, CAPITAL_PAYMENT, 1000.00, EUR),
        new ApiCapitalEvent(event2.accountingDate, CAPITAL_PAYMENT, 0.12, EUR),
        new ApiCapitalEvent(event3.accountingDate, MEMBERSHIP_BONUS, 2000.00, EUR)
    ]
  }

  def "getConcentrationLimit"() {
    def ownershipUnitPrice = 1.567890
    aggregatedCapitalEventRepository.findTopByOrderByDateDesc() >>
        getAggregatedCapitalEvent(ownershipUnitPrice)

    when:
    BigDecimal concentrationUnitLimit = service.getCapitalConcentrationUnitLimit()

    then:
    concentrationUnitLimit.compareTo(BigDecimal.TEN) == 0
  }

  private AggregatedCapitalEvent getAggregatedCapitalEvent(BigDecimal ownershipUnitPrice) {
    new AggregatedCapitalEvent(0,
        INVESTMENT_RETURN,
        new BigDecimal(1),
        new BigDecimal(1),
        new BigDecimal(100),
        ownershipUnitPrice,
        LocalDate.now()
    )
  }
}
