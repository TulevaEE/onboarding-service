package ee.tuleva.comparisons;

import ee.tuleva.comparisons.exceptions.IsinNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;


@Service
public class ComparisonService {

    private static int estonianAgeOfRetirement = 65;
    private static double estonianDepositRate = 0.06;
    private static double returnRate = 0.05;
    private static double gainRate = 0.03;

    @Autowired
    @Resource
    private ComparisonDAO comparisonDAO;

    private double totalFee(double totalCapital, int age, double monthlyWage, String isin) throws IsinNotFoundException{

        int n = estonianAgeOfRetirement - age;

        double monthlyDeposit = estonianDepositRate * monthlyWage;

        double yearlyDeposit = monthlyDeposit * 12;

        double fvx = fv(totalCapital,n, yearlyDeposit,gainRate,returnRate);

        double rNet = returnRate - comparisonDAO.getFee(isin);

        double fvy = fv(totalCapital,n, yearlyDeposit,gainRate,rNet);

        double totalFee = fvx - fvy;

        DecimalFormat df = new DecimalFormat("#.##");
        totalFee = Double.valueOf(df.format(totalFee));
        return totalFee;

    }

    private static double fv(double pmt, int n, double w, double g, double r){
        return w*(Math.pow(1+r,n)-Math.pow(1+g,n)) / 1-g + pmt * Math.pow(1+r,n);
    }

    public Map<String, String> comparedResults (ComparisonCommand cm) throws IsinNotFoundException{

        String isin = cm.getIsin();
        int age = cm.getAge();
        double monthlyWage = cm.getMonthlyWage();
        double totalCapital = cm.getMonthlyWage();

        double totalFee = totalFee(totalCapital, age, monthlyWage, isin);

        Map<String, String> rdata = new HashMap<>();
        rdata.put("isin", isin);
        rdata.put("totalFee", Double.toString(totalFee));
        rdata.put("currency", "EUR");

        return rdata;
    }


}
