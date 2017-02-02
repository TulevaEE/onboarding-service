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
import java.util.Map;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @ResponseBody
    @RequestMapping(path = "/comparisons/", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public Comparison comparison(@Valid @ModelAttribute @ApiParam ComparisonCommand comparisonCommand,
                                          @ApiIgnore Errors errors) throws Exception {

        if (errors != null && errors.hasErrors()) {
            throw new ErrorsValidationException(errors);
        }
        return comparisonService.comparedResults(comparisonCommand);

    }

}
