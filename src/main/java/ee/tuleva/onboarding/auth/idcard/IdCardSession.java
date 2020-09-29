package ee.tuleva.onboarding.auth.idcard;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@RequiredArgsConstructor
@Data
@Builder
public class IdCardSession implements Person, Serializable {

    private static final long serialVersionUID = -111852724795571891L;

    public final String firstName;
    public final String lastName;
    public final String personalCode;
    public final IdDocumentType documentType;

}