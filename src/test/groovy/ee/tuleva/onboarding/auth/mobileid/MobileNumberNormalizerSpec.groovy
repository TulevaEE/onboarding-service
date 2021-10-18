package ee.tuleva.onboarding.auth.mobileid

import spock.lang.Specification

class MobileNumberNormalizerSpec extends Specification {

  MobileNumberNormalizer normalizer = new MobileNumberNormalizer()

  def "normalizes Estonian phone numbers: [#phoneNumber]"() {
    when:
    def returnedNumber = normalizer.normalizePhoneNumber(phoneNumber)

    then:
    returnedNumber == normalizedPhoneNumber

    where:
    phoneNumber    | normalizedPhoneNumber
    null           | null
    ""             | null
    "   "          | null
    "+"            | null
    "  +  "        | null
    "+372"         | null
    "  +372   "    | null
    "+37255667788" | "+37255667788"
    "37255667788"  | "+37255667788"
    "5xxxxxx"      | "+3725xxxxxx"
    "5xxxxxxx"     | "+3725xxxxxxx"
    "81xxxxxx"     | "+37281xxxxxx"
    "82xxxxxx"     | "+37282xxxxxx"
    "83xxxxxx"     | "+37283xxxxxx"
    "84xxxxxx"     | "+37284xxxxxx"
    "870xxxxxxxxx" | "+372870xxxxxxxxx"
    "871xxxxxxxxx" | "+372871xxxxxxxxx"
    "+37200000766" | "+37200000766"
    "37200000766"  | "+37200000766"
    "00000766"     | "+37200000766"
  }
}
