package ee.tuleva.onboarding.investment.report.publishing.wordpress

import spock.lang.Specification
import spock.lang.Unroll

class WordPressMediaClientSpec extends Specification {

  @Unroll
  def "toWordPressSlug(#filename) == #expectedSlug"() {
    expect:
    WordPressMediaClient.toWordPressSlug(filename) == expectedSlug
    expectedSlug ==~ /[a-z0-9-]+\.[a-z0-9]+/

    where:
    filename                                                                    || expectedSlug
    "normal.pdf"                                                                || "normal.pdf"
    "Report 2026-03.pdf"                                                        || "report-2026-03.pdf"
    "Tuleva Maailma Aktsiate Pensionifondi investeeringute aruanne 2026-03.pdf" || "tuleva-maailma-aktsiate-pensionifondi-investeeringute-aruanne-2026-03.pdf"
    "V\u00f5lakirjade fond.pdf"                                                 || "volakirjade-fond.pdf"
    "file\"name.pdf"                                                            || "file-name.pdf"
    "file\\name.pdf"                                                            || "file-name.pdf"
    "report\rinjected.pdf"                                                      || "report-injected.pdf"
    "report\ninjected.pdf"                                                      || "report-injected.pdf"
    "report;rm -rf.pdf"                                                         || "report-rm-rf.pdf"
    "report/x.pdf"                                                              || "report-x.pdf"
    "../../etc/passwd.pdf"                                                      || "etc-passwd.pdf"
    "report%2e%2e%2fx.pdf"                                                      || "report-2e-2e-2fx.pdf"
    "C:\\Windows\\x.pdf"                                                        || "c-windows-x.pdf"
    "report\u202egpj.pdf"                                                       || "report-gpj.pdf"
    "report\u200bx.pdf"                                                         || "report-x.pdf"
    "rep\u0430ort.pdf"                                                          || "rep-ort.pdf"
    "\u0130stanbul.pdf"                                                         || "istanbul.pdf"
    "ra\u0131port.pdf"                                                          || "ra-port.pdf"
    "\u03a3igma.pdf"                                                            || "igma.pdf"
    "ra\u03c2port.pdf"                                                          || "ra-port.pdf"
    "\u0130\u0131\u03a3\u03c2 raport.pdf"                                       || "i-raport.pdf"
    "e\u0301clair.pdf"                                                          || "eclair.pdf"
    "\u00e9clair.pdf"                                                           || "eclair.pdf"
    "a.b.pdf"                                                                   || "a-b.pdf"
    ".pdf"                                                                      || "pdf.pdf"
    "a" * 140 + ".pdf"                                                          || "a" * 100 + ".pdf"
    "report.pdf\"; x=\"y"                                                       || "report.pdfxy"
    "report.pdf\r\nX-Injected: 1"                                               || "report.pdfxinjected1"
    "  \u00c4\"wild\\\r\n name??  .p d\"f  "                                    || "a-wild-name.pdf"
    "report.p-df"                                                               || "report.pdf"
    "report.PDF"                                                                || "report.pdf"
    "report."                                                                   || "report.pdf"
    "report"                                                                    || "report.pdf"
  }

  @Unroll
  def "toWordPressSlug rejects a filename that sanitises to nothing: #filename"() {
    when:
    WordPressMediaClient.toWordPressSlug(filename)

    then:
    thrown(IllegalArgumentException)

    where:
    filename << ["", ".", "-", "   ", "....", "???.pdf", "---.pdf", "\u202e.pdf"]
  }
}
