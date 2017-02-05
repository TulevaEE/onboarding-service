package ee.tuleva.onboarding.comparisons.exceptions;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FundManagerNotFoundException extends ComparisonException {

    private String managername;

}
