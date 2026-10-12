package ee.tuleva.onboarding.investment.report.publishing

import spock.lang.Specification
import spock.lang.Unroll

import java.time.YearMonth

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75

class ReportPublicationSpec extends Specification {

  private static final String UPLOADS = "https://tuleva.ee/wp-content/uploads/2026/10/"
  private static final YearMonth AUGUST = YearMonth.of(2026, 8)
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9)

  @Unroll
  def "a page linking #url has published the 2026-09 report: #published"() {
    expect:
    ReportPublication.linking(TUK75, url, SEPTEMBER, SEPTEMBER).isPublished() == published

    where:
    url                                                                                   || published
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf" || true
    UPLOADS + "Tuleva-Maailma-Aktsiate-Pensionifondi-investeeringute-aruanne-2026-09.pdf" || true
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09-1.pdf" || true
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-08.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2025-09.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-13.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-12026-09.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-091.pdf" || false
    UPLOADS + "Tuleva-Maailma-Aktsiate-Pensionifondi-investeeringute-aruanne-september-2026.pdf" || false
    UPLOADS + "investeeringute-aruanne.pdf"                                               || false
    UPLOADS + "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-09.pdf" || false
    UPLOADS + "tuleva-iii-samba-pensionifondi-investeeringute-aruanne-2026-09.pdf"        || false
    UPLOADS + "kuukokkuvote-2026-09.pdf"                                                  || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-draft-2026-09.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-lisa-2026-09.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.docx" || false
    "https://tuleva.ee/investeeringute-aruanne.pdf"                                       || false
  }

  def "a published report and an outdated one are told apart by the month the filename names"() {
    given:
    def september = UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf"
    def august = UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-08.pdf"

    expect:
    ReportPublication.linking(TUK75, september, SEPTEMBER, SEPTEMBER) == new ReportPublication.Published(TUK75, september)
    ReportPublication.linking(TUK75, august, SEPTEMBER, SEPTEMBER) == new ReportPublication.Outdated(TUK75, august, SEPTEMBER)
  }

  def "a report uploaded under its accented title is read through its URL encoding"() {
    expect:
    ReportPublication.linking(
        TUK00,
        UPLOADS + "Tuleva-Maailma-V%C3%B5lakirjade-Pensionifondi-investeeringute-aruanne-2026-09.pdf",
        SEPTEMBER,
        SEPTEMBER).isPublished()
  }

  def "another fund's report on the page is told apart from an outdated one, and counts as missing"() {
    given:
    def bondFundsSeptember = UPLOADS + "tuleva-maailma-volakirjade-pensionifondi-investeeringute-aruanne-2026-09.pdf"

    when:
    def publication = ReportPublication.linking(TUK75, bondFundsSeptember, SEPTEMBER, SEPTEMBER)

    then:
    publication == new ReportPublication.ForeignFile(TUK75, bondFundsSeptember)
    publication.isMissing()
  }

  @Unroll
  def "while August is required and September is last month, a page linking the #named report has published it: #published"() {
    expect:
    ReportPublication.linking(
        TUK75,
        UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-${named}.pdf",
        AUGUST,
        SEPTEMBER).isPublished() == published

    where:
    named     || published
    "2026-07" || false
    "2026-08" || true
    "2026-09" || true
    "2026-10" || false
    "2027-09" || false
  }

  def "a report naming a month after last month is not this fund's report, and the red line names its file"() {
    given:
    def typoedSeptember = UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2027-09.pdf"

    when:
    def publication = ReportPublication.linking(TUK75, typoedSeptember, SEPTEMBER, SEPTEMBER)

    then:
    publication == new ReportPublication.ForeignFile(TUK75, typoedSeptember)
    publication.isMissing()
    publication.describe() == "TUK75: the fund page links tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2027-09.pdf, which is not this fund's investment report"
  }
}
