package ee.tuleva.onboarding.investment.report.publishing

import spock.lang.Specification
import spock.lang.Unroll

import java.time.YearMonth

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75

class ReportPublicationSpec extends Specification {

  private static final String UPLOADS = "https://tuleva.ee/wp-content/uploads/2026/10/"
  private static final String SEPTEMBER_UPLOADS = "https://tuleva.ee/wp-content/uploads/2026/09/"

  @Unroll
  def "a page linking #url has published the 2026-09 report: #published"() {
    expect:
    ReportPublication.linking(TUK75, url, YearMonth.of(2026, 9)).isPublished() == published

    where:
    url                                                                                   || published
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf" || true
    UPLOADS + "Tuleva-Maailma-Aktsiate-Pensionifondi-investeeringute-aruanne-2026-09.pdf" || true
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09-1.pdf" || true
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-10.pdf" || true
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-08.pdf" || false
    UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2025-09.pdf" || false
    SEPTEMBER_UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-13.pdf" || false
    SEPTEMBER_UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-12026-09.pdf" || false
    SEPTEMBER_UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-091.pdf" || false
    UPLOADS + "Tuleva-Maailma-Aktsiate-Pensionifondi-investeeringute-aruanne-september-2026.pdf" || true
    UPLOADS + "investeeringute-aruanne.pdf"                                               || true
    SEPTEMBER_UPLOADS + "investeeringute-aruanne.pdf"                                     || false
    "https://tuleva.ee/investeeringute-aruanne.pdf"                                       || false
  }

  def "a published report and an outdated one are told apart by the month the filename names"() {
    given:
    def september = UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-09.pdf"
    def august = UPLOADS + "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-08.pdf"

    expect:
    ReportPublication.linking(TUK75, september, YearMonth.of(2026, 9)) == new ReportPublication.Published(TUK75, september)
    ReportPublication.linking(TUK75, august, YearMonth.of(2026, 9)) == new ReportPublication.Outdated(TUK75, august)
  }
}
