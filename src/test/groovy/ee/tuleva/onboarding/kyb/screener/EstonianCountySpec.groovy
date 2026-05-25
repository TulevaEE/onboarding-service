package ee.tuleva.onboarding.kyb.screener

import spock.lang.Specification
import spock.lang.Unroll

class EstonianCountySpec extends Specification {

    @Unroll
    def "detects Estonian county in address '#address' -> #present"() {
        expect:
        EstonianCounty.isPresentIn(address) == present
        where:
        address                                      | present
        "Tartu maakond, Tartu linn, Paju 2"          | true
        "Paju 2, 50104 Tartumaa"                      | true
        "Kuressaare, Saaremaa"                        | true
        "Ida-Viru maakond, Narva linn"                | true
        "Rakvere, Lääne-Virumaa"                      | true
        "Kärdla, Hiiumaa"                             | true
        "HARJU MAAKOND, Tallinn"                      | true
        "Pärnu mnt 1, 11313 Tallinn"                  | false
        "10 Downing Street, London, UK"              | false
        ""                                            | false
        null                                          | false
    }
}
