package ee.tuleva.onboarding.comparisons;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class Comparison implements Serializable{

    private String isin;

    private float totalFee;

    private String currency;

    public Comparison(String isin, float totalFee) {
        this.isin = isin;
        this.totalFee = totalFee;
        this.currency = "EUR";
    }

}
