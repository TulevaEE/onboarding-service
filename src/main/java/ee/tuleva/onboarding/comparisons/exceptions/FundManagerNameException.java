package ee.tuleva.onboarding.comparisons.exceptions;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FundManagerNameException extends Exception {

    private String managername;

}
