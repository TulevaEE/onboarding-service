package ee.tuleva.onboarding.rejoin;

import ee.tuleva.onboarding.epis.RejoinPermission;
import ee.tuleva.onboarding.epis.RejoinPermissionSource;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class RejoinController {

  private final RejoinPermissionSource rejoinPermissionSource;

  @Operation(summary = "Whether the logged-in person may rejoin the II pillar")
  @GetMapping("/rejoin-permission")
  public RejoinPermission getRejoinPermission() {
    return rejoinPermissionSource.getRejoinPermission();
  }
}
