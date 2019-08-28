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

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReturnsController {

    private final ReturnsService returnsService;

    @GetMapping("/returns")
    public Returns getReturns(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson person,
                              @RequestParam @DateTimeFormat(iso = DATE) LocalDate from) {
        return returnsService.get(person, from);
    }
}
