package ee.tuleva.onboarding.auth.idcard;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@RequiredArgsConstructor
@Data
@Builder
public class IdCardSession implements Serializable {

    public final String firstName;
    public final String lastName;
    public final String personalCode;
    public final IdDocumentType documentType;

    @Override
    public String toString() {
        return firstName + ":::" + lastName + ":::" + personalCode + ":::" + documentType;
    }
}