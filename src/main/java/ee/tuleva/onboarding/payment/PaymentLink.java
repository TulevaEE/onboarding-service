package ee.tuleva.onboarding.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RedirectLink.class, name = "REDIRECT"),
  @JsonSubTypes.Type(value = PrefilledLink.class, name = "PREFILLED")
})
public sealed interface PaymentLink permits RedirectLink, PrefilledLink {

  String url();
}
