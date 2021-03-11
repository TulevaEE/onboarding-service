package ee.tuleva.onboarding.mandate.transfer;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.locale.LocaleService;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Deprecated
public class TransferExchangeController {

  private final TransferExchangeService transferExchangeService;
  private final LocaleService localeService;

  @ApiOperation(value = "Get transfer exchanges")
  @RequestMapping(method = GET, value = "/transfer-exchanges")
  public List<TransferExchangeDto> get(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam("status") ApplicationStatus status) {
    return transferExchangeService.get(authenticatedPerson).stream()
        .filter(transferExchange -> transferExchange.getStatus().equals(status))
        .map(
            transferExchange ->
                new TransferExchangeDto(transferExchange, localeService.getLanguage()))
        .collect(Collectors.toList());
  }
}
