package ee.tuleva.onboarding.comparisons;

import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.Entity;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
public class ComparisonCommand {

    @Min(0)
    Float totalCapital;

    @Min(0)
    @Max(65)
    Integer age;

    @Min(0)
    Float monthlyWage;

    @Size(min = 12, max = 12)
    String isin;

    public Float getTotalCapital() {
        return totalCapital;
    }

    public Integer getAge() {
        return age;
    }

    public Float getMonthlyWage() {
        return monthlyWage;
    }

    public String getIsin() {
        return isin;
    }
}
