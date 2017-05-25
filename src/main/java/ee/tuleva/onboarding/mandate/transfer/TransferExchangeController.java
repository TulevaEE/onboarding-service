package ee.tuleva.onboarding.mandate.transfer;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TransferExchangeController {

    private final TransferExchangeService transferExchangeService;

    @ApiOperation(value = "Get transfer exchanges")
    @RequestMapping(method = GET, value = "/transfer-exchanges")
    public List<TransferExchange> get(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                         @RequestParam("status") MandateApplicationStatus status) {
        return transferExchangeService.get(authenticatedPerson).stream()
                .filter(transferExchange -> transferExchange.getStatus().equals(status))
                .collect(Collectors.toList());
    }

}
