package ee.tuleva.onboarding.comparisons;

import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;


@Service
public class ComparisonService {

    private static final int estonianAgeOfRetirement = 65;
    private static final float estonianContributionRate = 0.06f; //percent from monthly wage
    private static final float returnRate = 0.05f;
    private static final float gainRate = 0.03f;

    @Autowired
    private FundRepository fundRepository;

    protected float totalFee(float totalCapital, int age, float monthlyWage, float managementFee) {

        int n = estonianAgeOfRetirement - age;

        float yearlyContribution = estonianContributionRate * monthlyWage * 12;

        float fvx = fv(yearlyContribution,n,totalCapital,gainRate,returnRate);

        float fvy = fv(yearlyContribution,n,totalCapital,gainRate,returnRate - managementFee);

        float totalFee = fvx - fvy;

        DecimalFormat df = new DecimalFormat("#.##");
        totalFee = Float.valueOf(df.format(totalFee));

        return totalFee;

    }

    protected static float fv(float yearly, int n, float totalCapital, float g, float r) {
        return yearly * ((float) Math.pow(1 + r, n) - (float) Math.pow(1 + g, n)) / (r - g) + (totalCapital * (float) Math.pow(1 + r, n));
    }

    public Comparison comparedResults (ComparisonCommand cm) throws IsinNotFoundException{

        Fund f = fundRepository.findByIsin(cm.getIsin());

        if (f == null){
            throw new IsinNotFoundException("Unrecognized ISIN");
        }

        float managementFee = f.getManagementFeeRate().floatValue();

        float totalFee = totalFee(cm.getTotalCapital(), cm.getAge(), cm.getMonthlyWage(), managementFee);

        return new Comparison(cm.getIsin(),totalFee);
    }


}
