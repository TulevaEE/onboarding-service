package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;


@Service
public class ComparisonService {

    private static int estonianAgeOfRetirement = 65;
    private static float estonianDepositRate = 0.06f;
    private static float returnRate = 0.05f;
    private static float gainRate = 0.03f;

    @Autowired
    @Resource
    private ComparisonDAO comparisonDAO;

    private float totalFee(float totalCapital, int age, float monthlyWage, String isin) throws IsinNotFoundException{

        int n = estonianAgeOfRetirement - age;

        float monthlyDeposit = estonianDepositRate * monthlyWage;

        float yearlyDeposit = monthlyDeposit * 12;

        float fvx = fv(totalCapital,n, yearlyDeposit,gainRate,returnRate);

        float rNet = returnRate - comparisonDAO.getFee(isin);

        float fvy = fv(totalCapital,n, yearlyDeposit,gainRate,rNet);

        float totalFee = fvx - fvy;

        DecimalFormat df = new DecimalFormat("#.##");
        totalFee = Float.valueOf(df.format(totalFee));
        return totalFee;

    }

    private static float fv(float pmt, int n, float w, float g, float r){
        return w*((float)Math.pow(1+r,n)-(float)Math.pow(1+g,n)) / 1-g + pmt * (float)Math.pow(1+r,n);
    }

    //public Map<String, String> comparedResults (ComparisonCommand cm) throws IsinNotFoundException{
    public Comparison comparedResults (ComparisonCommand cm) throws IsinNotFoundException{

        String isin = cm.getIsin();
        int age = cm.getAge();
        float monthlyWage = cm.getMonthlyWage();
        float totalCapital = cm.getMonthlyWage();

        float totalFee = totalFee(totalCapital, age, monthlyWage, isin);

        //Map<String, String> rdata = new HashMap<>();
        //rdata.put("isin", isin);
        //rdata.put("totalFee", Float.toString(totalFee));
        //rdata.put("currency", "EUR");

        //return rdata;
        return new Comparison(isin,totalFee);
    }


}
