package ee.tuleva.onboarding.comparisons;


import ee.tuleva.onboarding.comparisons.exceptions.ErrorsValidationException;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@RestController
@AllArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @ResponseBody
    @RequestMapping(path = "/v1/comparisons", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ArrayList<Comparison> comparison(@Valid @ModelAttribute @ApiParam ComparisonCommand comparisonCommand,
                                            @ApiIgnore Errors errors) throws Exception {

        if (errors != null && errors.hasErrors()) {
            throw new ErrorsValidationException(errors);
        }
        return new ArrayList<>(Collections.singletonList(comparisonService.comparedResults(comparisonCommand)));

    }

}
