package ee.tuleva.comparisons;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Getter
@Setter
public class ComparisonCommand {

    @Min(0)
    Double totalCapital;
    @Min(0)
    Integer age;
    @Min(0)
    Double monthlyWage;
    @Size(min = 12, max = 12)
    String isin;

    public Double getTotalCapital() {
        return totalCapital;
    }

    public Integer getAge() {
        return age;
    }

    public Double getMonthlyWage() {
        return monthlyWage;
    }

    public String getIsin() {
        return isin;
    }
}
