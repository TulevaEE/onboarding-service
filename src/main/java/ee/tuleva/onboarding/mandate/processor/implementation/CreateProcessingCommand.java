package ee.tuleva.onboarding.mandate.processor.implementation;

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
