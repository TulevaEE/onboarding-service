package ee.tuleva.comparisons;


import ee.tuleva.comparisons.exceptions.IsinNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class ComparisonDAO {

    private static Map<Integer, String> codeToIsin;

    static {
        codeToIsin = new HashMap<>();
        codeToIsin.put(44, "EE3600019790"); //Pension Fund LHV 25
        codeToIsin.put(35, "EE3600019808"); //Pension Fund LHV 50
        codeToIsin.put(73, "EE3600109401"); //Pension Fund LHV Index
        codeToIsin.put(45, "EE3600019816"); //Pension Fund LHV Interest
        codeToIsin.put(47, "EE3600019832"); //Pension Fund LHV L
        codeToIsin.put(39, "EE3600019774"); //Pension Fund LHV M
        codeToIsin.put(46, "EE3600019824"); //Pension Fund LHV S
        codeToIsin.put(38, "EE3600019766"); //Pension Fund LHV XL
        codeToIsin.put(59, "EE3600019782"); //Pension Fund LHV XS
        codeToIsin.put(48, "EE3600098430"); //Nordea Pension Fund A
        codeToIsin.put(57, "EE3600103503"); //Nordea Pension Fund A Plus
        codeToIsin.put(49, "EE3600098448"); //Nordea Pension Fund B
        codeToIsin.put(50, "EE3600098455"); //Nordea Pension Fund C
        codeToIsin.put(56, "EE3600103297"); //SEB Energetic Pension Fund
        codeToIsin.put(75, "EE3600109427"); //SEB Energetic Pension Fund Index
        codeToIsin.put(60, "EE3600019717"); //SEB Conservative Pension Fund
        codeToIsin.put(51, "EE3600098612"); //SEB Optimal Pension Fund
        codeToIsin.put(61, "EE3600019725"); //SEB Progressive Pension Fund
        codeToIsin.put(58, "EE3600019733"); //Swedbank Pension Fund K1 (Conservative Strategy)
        codeToIsin.put(36, "EE3600019741"); //Swedbank Pension Fund K2 (Balanced Strategy)
        codeToIsin.put(37, "EE3600019758"); //Swedbank Pension Fund K3 (Growth Strategy)
        codeToIsin.put(52, "EE3600103248"); //Swedbank Pension Fund K4 (Equity Strategy)
        codeToIsin.put(74, "EE3600109393"); //Swedbank Pension Fund K90-99 (Life-cycle Strategy)

    }

    private Map<String, Double> EstonianPensionFundFee = new HashMap<>();

    public Double getFee(String isin) throws IsinNotFoundException{
        if (!EstonianPensionFundFee.containsKey(isin)){
            throw new IsinNotFoundException("ISIN not valid or we have no data about it");
        }
        return EstonianPensionFundFee.get(isin);
    }

    public String getIsin(int code) {
        return codeToIsin.get(code);
    }

    public void addFee(String isin, double fee) {
        EstonianPensionFundFee.put(isin, fee);
    }

    public void addFee(int code, double fee) {
        EstonianPensionFundFee.put(getIsin(code), fee);
    }
}
