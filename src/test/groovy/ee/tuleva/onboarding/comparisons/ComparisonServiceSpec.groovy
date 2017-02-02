package ee.tuleva.onboarding.comparisons


import spock.lang.Specification

public class ComparisonServiceSpec extends Specification {

    def service = new ComparisonService()

    def "totalfee calculations work" (float totalCapital, int age, float monthlyWage, float fee, float c){
        expect:
        service.totalFee(totalCapital,age,monthlyWage,fee) == c

        where:
        totalCapital | age | monthlyWage | fee     | c
        10000        | 30  | 1200        | 0.0075  | 26801.58
        10000        | 30  | 1200        | 0.0039  | 14606.8
        10000        | 30  | 1200        | 0.0092  | 32166.11

    }

}
