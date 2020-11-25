package ee.tuleva.onboarding.aml;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.tuleva.onboarding.aml.command.AmlCheckAddCommand;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/v1/amlchecks")
@Slf4j
@RequiredArgsConstructor
class AmlCheckController {

    private final AmlCheckService amlCheckService;

    @GetMapping
    @ApiOperation(value = "Get missing AML checks")
    public List<AmlCheckResponse> getMissing(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
        Long userId = authenticatedPerson.getUserId();
        return amlCheckService.getMissingChecks(userId).stream()
            .map(type -> AmlCheckResponse.builder().type(type).success(false).build())
            .collect(toList());
    }

    @PostMapping
    @ApiOperation(value = "Add manual AML check")
    public AmlCheckResponse addManualCheck(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                           @Valid @RequestBody AmlCheckAddCommand command) {
        Long userId = authenticatedPerson.getUserId();
        amlCheckService.addCheckIfMissing(userId, command);
        return new AmlCheckResponse(command);
    }

    @Value
    @Builder
    @AllArgsConstructor
    static class AmlCheckResponse {
        AmlCheckType type;
        boolean success;
        @JsonInclude(NON_NULL)
        Map<String, Object> metadata;

        public AmlCheckResponse(AmlCheckAddCommand command) {
            this.type = command.getType();
            this.success = command.isSuccess();
            this.metadata = command.getMetadata();
        }
    }
}
