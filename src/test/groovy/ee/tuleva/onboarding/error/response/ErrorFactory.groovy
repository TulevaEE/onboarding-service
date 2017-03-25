package ee.tuleva.onboarding.error.response

import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors

class ErrorFactory {

    public static Errors manufactureErrors(def target) {
        return new BeanPropertyBindingResult(target, "object")
    }

}
