package ee.tuleva.comparisons;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ComparisonController {

    @Autowired
    private ComparisonService comparisonService;

    @Autowired
    private EstonianFeeFinderService estonianFeeFinderService;

    @RequestMapping(path = "v1/comparisons/", method = RequestMethod.GET)
    public Map<String, String> comparison(@RequestParam(value = "totalCapital") Double totalCapital, @RequestParam(value = "age") Integer age,
                                          @RequestParam(value = "isin") String isin, @RequestParam(value = "monthlyWage") Double monthlyWage){


        return comparisonService.comparedResults(totalCapital, age, isin, monthlyWage);

    }

}
