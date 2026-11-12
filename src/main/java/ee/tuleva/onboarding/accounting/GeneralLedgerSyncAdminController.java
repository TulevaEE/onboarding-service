package ee.tuleva.onboarding.accounting;

import static org.springframework.http.HttpStatus.ACCEPTED;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/general-ledger")
@RequiredArgsConstructor
@Profile("production")
class GeneralLedgerSyncAdminController {

  private final AdminTokenValidator tokenValidator;
  private final GeneralLedgerBackgroundSync backgroundSync;

  @PostMapping("/sync")
  @ResponseStatus(ACCEPTED)
  String sync(@RequestHeader("X-Admin-Token") String token) {
    tokenValidator.validate(token);
    log.info("Admin triggered general ledger sync");
    backgroundSync.start();
    return "General ledger sync started, unless another sync holds its lock;"
        + " the log has each entity's result";
  }
}
