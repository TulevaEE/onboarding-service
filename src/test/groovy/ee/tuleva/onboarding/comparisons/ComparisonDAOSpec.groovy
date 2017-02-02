package ee.tuleva.onboarding.comparisons

import spock.lang.Specification

import java.lang.reflect.Field

class ComparisonDAOSpec extends Specification{

    def comparisondao = Mock(ComparisonDAO)

    def setup(){
        Field field = ComparisonDAO.getDeclaredField("EstonianPensionFundFee")
        field.setAccessible(true);
    }

/*    @Unroll
    def "finding fee by isin works" (String isin, float f){

        when:
        comparisondao.addFee("testisin---1",1.0f)
        comparisondao.addFee("testisin---2",2.0f)
        comparisondao.addFee("testisin---3",3.0f)

        then:
        comparisondao.getFee(isin) == f

        where:
        isin           | f
        "testisin---1" | 1.0f
        "testisin---2" | 2.0f
        "testisin---3" | 3.0f

    }*/

    def "not finding isin throws exception" (){



    }
}
