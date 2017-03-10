package ee.tuleva.onboarding.comparisons;


import ee.tuleva.onboarding.comparisons.exceptions.ErrorsValidationException;
import ee.tuleva.onboarding.income.Money;
import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @ResponseBody
    @RequestMapping(path = "comparisons", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public Money comparison(@Valid @ModelAttribute @ApiParam ComparisonCommand comparisonCommand,
                            @ApiIgnore @AuthenticationPrincipal User user,
                            @ApiIgnore Errors errors) throws Exception {
        if (errors != null && errors.hasErrors()) {
            throw new ErrorsValidationException(errors);
        }

        return comparisonService.compare(comparisonCommand, user);
    }

}
