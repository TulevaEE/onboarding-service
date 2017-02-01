package ee.tuleva.comparisons;


import ee.tuleva.comparisons.exceptions.ErrorsValidationException;
import ee.tuleva.comparisons.exceptions.IsinNotFoundException;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ComparisonController {

    @Autowired
    private ComparisonService comparisonService;

    @RequestMapping(path = "/comparisons/", method = RequestMethod.GET)
    public Map<String, String> comparison(@Valid @ModelAttribute @ApiParam ComparisonCommand comparisonCommand,
                                          @ApiIgnore Errors errors) throws Exception {

        if (errors != null && errors.hasErrors()) {
            throw new ErrorsValidationException(errors);
        }
        return comparisonService.comparedResults(comparisonCommand);

    }

}
