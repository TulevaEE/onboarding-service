package ee.tuleva.onboarding.investment.admin;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.HttpStatus.CONFLICT;

import ee.tuleva.onboarding.investment.fees.ocf.OcfPublicationRefusedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = OcfPublicationAdminController.class)
class OcfPublicationRefusalAdvice {

  @ExceptionHandler(OcfPublicationRefusedException.class)
  ResponseEntity<String> refuse(OcfPublicationRefusedException refusal) {
    var reason = requireNonNullElse(refusal.getMessage(), refusal.toString());
    log.info("OCF publication refused: reason={}", reason);
    return ResponseEntity.status(CONFLICT).body(reason);
  }
}
