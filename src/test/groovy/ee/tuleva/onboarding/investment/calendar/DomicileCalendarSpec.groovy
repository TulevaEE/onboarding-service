package ee.tuleva.onboarding.investment.calendar

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

import static ee.tuleva.onboarding.investment.calendar.Domicile.FRANCE
import static ee.tuleva.onboarding.investment.calendar.Domicile.IRELAND
import static ee.tuleva.onboarding.investment.calendar.Domicile.LUXEMBOURG

class DomicileCalendarSpec extends Specification {

  DomicileCalendar domicileCalendar = new DomicileCalendar(new Target2Calendar())

  @Unroll
  def "#domicile: isBusinessDay(#date) == #expected"() {
    expect:
    domicileCalendar.forDomicile(domicile).isBusinessDay(LocalDate.parse(date)) == expected

    where:
    domicile   | date         | expected
    IRELAND    | "2026-03-17" | false // St Patrick's Day
    LUXEMBOURG | "2026-03-17" | true
    FRANCE     | "2026-03-17" | true
    LUXEMBOURG | "2026-06-23" | false // National Day
    IRELAND    | "2026-06-23" | true
    FRANCE     | "2026-06-23" | true
    LUXEMBOURG | "2025-05-29" | false // Ascension 2025 (Easter Apr 20 + 39)
    LUXEMBOURG | "2025-06-09" | false // Whit Monday 2025 (Easter Apr 20 + 50)
    LUXEMBOURG | "2026-05-14" | false // Ascension 2026 (Easter Apr 5 + 39)
    LUXEMBOURG | "2026-05-25" | false // Whit Monday 2026 (Easter Apr 5 + 50)
    LUXEMBOURG | "2035-05-03" | false // Ascension 2035 (Easter Mar 25 + 39)
    LUXEMBOURG | "2035-05-14" | false // Whit Monday 2035 (Easter Mar 25 + 50)
    LUXEMBOURG | "2025-05-09" | false // Europe Day
    LUXEMBOURG | "2025-08-15" | false // Assumption Day
    LUXEMBOURG | "2027-11-01" | false // All Saints' Day
    LUXEMBOURG | "2027-12-27" | true // no weekend substitution in Luxembourg
    IRELAND    | "2025-05-29" | true // Ascension is not an Irish holiday
    FRANCE     | "2025-05-29" | false // Ascension 2025
    FRANCE     | "2025-06-09" | false // Whit Monday 2025
    FRANCE     | "2035-05-03" | false // Ascension 2035
    FRANCE     | "2025-08-15" | false // Assumption Day
    FRANCE     | "2025-05-09" | true // Europe Day is not a French holiday
    FRANCE     | "2026-05-08" | false // Victory Day
    FRANCE     | "2026-07-14" | false // Bastille Day
    FRANCE     | "2027-11-01" | false // All Saints' Day
    FRANCE     | "2026-11-11" | false // Armistice Day
    IRELAND    | "2026-07-14" | true
    IRELAND    | "2026-04-03" | false // Good Friday via TARGET2
    LUXEMBOURG | "2026-04-06" | false // Easter Monday via TARGET2
    FRANCE     | "2026-05-01" | false // Labour Day via TARGET2
    IRELAND    | "2025-02-03" | false // St Brigid's Day 2025 (first Monday of February)
    IRELAND    | "2030-02-01" | false // St Brigid's Day 2030 (Feb 1 is a Friday)
    IRELAND    | "2030-02-04" | true // first Monday of February 2030 is not a holiday
    LUXEMBOURG | "2025-02-03" | true // St Brigid's Day is not a Luxembourg holiday
    IRELAND    | "2025-05-05" | false // May bank holiday (first Monday)
    IRELAND    | "2025-06-02" | false // June bank holiday (first Monday)
    IRELAND    | "2025-08-04" | false // August bank holiday (first Monday)
    IRELAND    | "2025-10-27" | false // October bank holiday (last Monday)
    IRELAND    | "2035-05-07" | false // May bank holiday 2035 (first Monday)
    FRANCE     | "2025-06-02" | true // June bank holiday is not a French holiday
    IRELAND    | "2029-03-19" | false // substitute Monday for St Patrick's Day on Saturday
    IRELAND    | "2027-12-27" | false // substitute Monday for Christmas Day on Saturday
    IRELAND    | "2027-12-28" | false // substitute Tuesday for St Stephen's Day on Sunday
    IRELAND    | "2028-01-03" | false // substitute Monday for New Year's Day on Saturday
    IRELAND    | "2026-01-10" | false // Saturday
    IRELAND    | "2026-01-12" | true
  }

  @Unroll
  def "#domicile: addBusinessDays(#date, #days) == #expected"() {
    expect:
    domicileCalendar.forDomicile(domicile).addBusinessDays(LocalDate.parse(date), days) ==
        LocalDate.parse(expected)

    where:
    domicile   | date         | days | expected
    IRELAND    | "2026-03-12" | 5    | "2026-03-20" // skips St Patrick's Day
    FRANCE     | "2026-07-08" | 5    | "2026-07-16" // skips Bastille Day
    LUXEMBOURG | "2026-06-17" | 5    | "2026-06-25" // skips National Day
    IRELAND    | "2026-04-01" | 2    | "2026-04-07" // skips Good Friday and Easter Monday
    LUXEMBOURG | "2026-05-13" | 1    | "2026-05-15" // skips Ascension 2026
    FRANCE     | "2025-06-06" | 1    | "2025-06-10" // skips weekend and Whit Monday 2025
    IRELAND    | "2025-01-31" | 1    | "2025-02-04" // skips weekend and St Brigid's Day
  }
}
