package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DecimalFormat;


@Service
public class ComparisonService {

    //TODO: make rates changeable
    private static int estonianAgeOfRetirement = 65;
    private static float estonianContributionRate = 0.06f;
    private static float returnRate = 0.05f;
    private static float gainRate = 0.03f;

    @Autowired
    @Resource
    private ComparisonDAO comparisonDAO;

    protected float totalFee(float totalCapital, int age, float monthlyWage, float managementFee) throws IsinNotFoundException{

        int n = estonianAgeOfRetirement - age;

        float yearlyContribution = estonianContributionRate * monthlyWage * 12;

        float fvx = fv(yearlyContribution,n,totalCapital,gainRate,returnRate);

        float rNet = returnRate - managementFee;

        float fvy = fv(yearlyContribution,n,totalCapital,gainRate,rNet);

        float totalFee = fvx - fvy;

        DecimalFormat df = new DecimalFormat("#.##");
        totalFee = Float.valueOf(df.format(totalFee));

        return totalFee;

    }

    protected static float fv(float yearly, int n, float totalCapital, float g, float r) {
        return yearly * ((float) Math.pow(1 + r, n) - (float) Math.pow(1 + g, n)) / (r - g) + (totalCapital * (float) Math.pow(1 + r, n));
    }

    public Comparison comparedResults (ComparisonCommand cm) throws IsinNotFoundException{

        String isin = cm.getIsin();

        float managementFee = comparisonDAO.getFee(isin)/100;

        float totalFee = totalFee(cm.getTotalCapital(), cm.getAge(), cm.getMonthlyWage(), managementFee);

        return new Comparison(isin,totalFee);
    }


}
