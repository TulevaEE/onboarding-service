package ee.tuleva.onboarding.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
public class LogoutController {

  @GetMapping(value = "/v1/logout")
  @ResponseStatus(HttpStatus.OK)
  public void logout(@RequestHeader(value = "Authorization") String authHeader) {
    String tokenValue = authHeader.replace("Bearer", "").trim();
    // TODO: token blacklist
  }
}
