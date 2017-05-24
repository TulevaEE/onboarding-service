package ee.tuleva.onboarding.mandate.command.application.transfer;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.processor.implementation.EpisService;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.MandateApplicationStatus;
import ee.tuleva.onboarding.mandate.processor.implementation.MandateApplication.TransferExchangeDTO;
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

    private final EpisService episService;

    @ApiOperation(value = "Get transfer exchanges")
    @RequestMapping(method = GET, value = "/transfer-exchanges")
    public List<TransferExchangeDTO> get(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                         @RequestParam("status") MandateApplicationStatus status) {
        return episService.getTransferApplications(authenticatedPerson).stream()
                .filter(transferExchange -> transferExchange.getStatus().equals(status))
                .collect(Collectors.toList());
    }

}
