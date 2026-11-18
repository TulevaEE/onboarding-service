package ee.tuleva.onboarding.party;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(UNPROCESSABLE_ENTITY)
public class NotAMinor extends RuntimeException {

  public NotAMinor() {
    super("Guardians are looked up for minors only");
  }
}
