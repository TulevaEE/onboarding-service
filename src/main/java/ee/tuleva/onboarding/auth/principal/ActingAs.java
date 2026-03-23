package ee.tuleva.onboarding.auth.principal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @Type(value = ActingAs.Person.class, name = "PERSON"),
  @Type(value = ActingAs.Company.class, name = "COMPANY")
})
public sealed interface ActingAs extends Serializable {

  String code();

  record Person(@NotBlank String code) implements ActingAs {}

  record Company(@NotBlank String code) implements ActingAs {}
}
