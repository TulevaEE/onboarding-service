package ee.tuleva.onboarding.comparisons;

import java.io.Serializable;


public class Comparison implements Serializable{

    private String isin;

    private float totalFee;

    private String currency;

    public Comparison(String isin, float totalFee) {
        this.isin = isin;
        this.totalFee = totalFee;
        this.currency = "EUR";
    }

    public String getIsin() {
        return isin;
    }

    public float getTotalFee() {
        return totalFee;
    }

    public String getCurrency() {
        return currency;
    }
}
