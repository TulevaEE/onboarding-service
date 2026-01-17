package ee.tuleva.onboarding.banking.xml;

import jakarta.xml.bind.JAXBContext;
import java.io.*;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
public class Iso20022Marshaller {

  @SneakyThrows
  public String marshalToString(Object object) {
    JAXBContext marshalContext =
        JAXBContext.newInstance(ee.tuleva.onboarding.banking.iso20022.camt060.ObjectFactory.class);
    var marshaller = marshalContext.createMarshaller();
    StringWriter sw = new StringWriter();
    marshaller.marshal(object, sw);
    return sw.toString();
  }

  @SneakyThrows
  public <T> T unMarshal(String response, Class<T> responseClass, Class objectFactoryClass) {
    JAXBContext unMarshalContext = JAXBContext.newInstance(objectFactoryClass);
    var unmarshaller = unMarshalContext.createUnmarshaller();
    return responseClass.cast(unmarshaller.unmarshal(new StringReader(response)));
  }
}
