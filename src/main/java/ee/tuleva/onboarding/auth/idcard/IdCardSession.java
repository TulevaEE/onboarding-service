package ee.tuleva.onboarding.auth.idcard;

import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
@Builder
public class IdCardSession implements Person, Serializable {

  public static final String ID_DOCUMENT_TYPE = "idDocumentType";

  @Serial private static final long serialVersionUID = -111852724795571891L;

  public final String firstName;
  public final String lastName;
  public final String personalCode;
  public final IdDocumentType documentType;
}
