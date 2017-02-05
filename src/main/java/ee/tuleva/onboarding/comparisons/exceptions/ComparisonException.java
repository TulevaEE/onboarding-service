package ee.tuleva.onboarding.comparisons.exceptions;


import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ComparisonException extends Exception{

    ComparisonException(String message) {
        super(message);
    }

}
