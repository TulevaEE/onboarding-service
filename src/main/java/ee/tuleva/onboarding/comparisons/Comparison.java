package ee.tuleva.onboarding.comparisons;

import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.Entity;
import java.io.Serializable;

@Data
@Entity
public class Comparison implements Serializable{

    private String isin;

    private float totalFee;

    private static String currency = "EUR";

    public Comparison(String isin, float totalFee) {
        this.isin = isin;
        this.totalFee = totalFee;
    }
}
