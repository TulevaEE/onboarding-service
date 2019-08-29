package ee.tuleva.onboarding.comparisons.returns;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReturnsController {

    static final LocalDate DEFAULT_DATE = LocalDate.parse("1900-01-01");

    private final ReturnsService returnsService;

    @GetMapping("/returns")
    public Returns getReturns(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson person,
                              @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
                              @RequestParam(required = false, name = "key") List<String> keys) {
        LocalDate startDate = (from == null) ? DEFAULT_DATE : from;
        return returnsService.get(person, startDate, keys);
    }
}
