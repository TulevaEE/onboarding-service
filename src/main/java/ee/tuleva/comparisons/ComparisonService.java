package ee.tuleva.comparisons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class ComparisonService {

    private static int estonianAgeOfRetirement = 65;
    private static double estonianDepositRate = 0.06;
    private double returnRate = 0.05;
    private double gainRate = 0.03;

    @Autowired
    ComparisonDAO comparisonDAO;

    private double totalFee(double totalCapital, int age, double monthlyWage, String isin) {

        double totalFee = 0;

        int n = estonianAgeOfRetirement - age;

        double monthlyDeposit = estonianDepositRate * monthlyWage;

        double yearlyDeposit = monthlyDeposit * 12;

        //double fvx = yearlyDeposit * ( Math.pow(1+returnRate,n) - Math.pow(1+gainRate,n)) / 1-gainRate + totalCapital * Math.pow(1+returnRate,n);
        double fvx = fv(totalCapital,n, yearlyDeposit,gainRate,returnRate);
        double rNet = returnRate - comparisonDAO.getFee(isin);

        double fvy = fv(totalCapital,n, yearlyDeposit,gainRate,rNet);
        //double fvy = yearlyDeposit * ( Math.pow(1+rNet,n) - Math.pow(1+gainRate,n)) / 1 - gainRate + totalCapital * Math.pow(1+rNet, n);

        totalFee = fvx - fvy;
        return totalFee;

    }

    private static double fv(double pmt, int n, double w, double g, double r){
        return w*(Math.pow(1+r,n)-Math.pow(1+g,n)) / 1-g + pmt * Math.pow(1+r,n);
    }

    public Map<String, String> comparedResults(double totalCapital, int age, String isin, double monthlyWage) {

        double totalFee = totalFee(totalCapital, age, monthlyWage, isin);

        Map<String, String> rdata = new HashMap<>();
        rdata.put("isin", isin);
        rdata.put("totalFee", Double.toString(totalFee));
        rdata.put("currency", "EUR");

        return rdata;
    }


}
