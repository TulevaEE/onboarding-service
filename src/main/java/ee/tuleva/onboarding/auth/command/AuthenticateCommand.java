package ee.tuleva.onboarding.auth.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @Type(value = MobileIdAuthenticateCommand.class, name = "MOBILE_ID"),
  @Type(value = SmartIdAuthenticateCommand.class, name = "SMART_ID"),
  @Type(value = IdCardAuthenticateCommand.class, name = "ID_CARD")
})
public sealed interface AuthenticateCommand
    permits MobileIdAuthenticateCommand, SmartIdAuthenticateCommand, IdCardAuthenticateCommand {}
