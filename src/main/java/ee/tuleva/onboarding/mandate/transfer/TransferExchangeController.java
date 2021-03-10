package ee.tuleva.onboarding.mandate.transfer;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
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

  @ApiOperation(value = "Get transfer exchanges")
  @RequestMapping(method = GET, value = "/transfer-exchanges")
  public List<TransferExchangeDto> get(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestHeader(value = "Accept-Language") String language,
      @RequestParam("status") ApplicationStatus status) {
    return transferExchangeService.get(authenticatedPerson).stream()
        .filter(transferExchange -> transferExchange.getStatus().equals(status))
        .map(transferExchange -> new TransferExchangeDto(transferExchange, language))
        .collect(Collectors.toList());
  }
}
