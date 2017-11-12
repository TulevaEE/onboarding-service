package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.mandate.content.MandateXmlMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CreateProcessingCommand {

    List<MandateXmlMessage> messages;

}
