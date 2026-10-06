package ee.tuleva.onboarding.auth.mobileid

import spock.lang.Specification

class MobileNumberNormalizerSpec extends Specification {

  MobileNumberNormalizer normalizer = new MobileNumberNormalizer()

  def "turns the ways people type an Estonian mobile number into one canonical form: [#typed]"() {
    expect:
    normalizer.normalize(typed) == canonical

    where:
    typed                  | canonical
    "+37255667788"         | "+37255667788"
    "37255667788"          | "+37255667788"
    "55667788"             | "+37255667788"
    "5566778"              | "+3725566778"
    "+3725566778"          | "+3725566778"
    "  55667788  "         | "+37255667788"
    "5566 7788"            | "+37255667788"
    "+372 5566 7788"       | "+37255667788"
    "+372 5566 7788" | "+37255667788"
    "5566-7788"            | "+37255667788"
    "5566.7788"            | "+37255667788"
    "(+372) 5566 7788"     | "+37255667788"
    "(372) 55667788"       | "+37255667788"
    "0037255667788"        | "+37255667788"
    "00 372 5566 7788"     | "+37255667788"
    "81234567"             | "+37281234567"
    "00000766"             | "+37200000766"
    "+37200000766"         | "+37200000766"
    "372 00000766"         | "+37200000766"
  }

  def "refuses what cannot be an Estonian mobile number: [#typed]"() {
    when:
    normalizer.normalize(typed)

    then:
    def exception = thrown(MobileIdException)
    exception.errorsResponse.errors*.code == ["mobile.id.phone.number.invalid"]

    where:
    typed << [
        "",
        "   ",
        "+",
        "+372",
        "555",
        "555666",
        "+358401234567",
        "00358401234567",
        "+3725566",
        "+372556677889",
        "870123456789",
        "+++37255667788",
        "5566+7788",
        "55667788+",
        "5566x788",
        "+37255667788 ext 1",
    ]
  }
}
