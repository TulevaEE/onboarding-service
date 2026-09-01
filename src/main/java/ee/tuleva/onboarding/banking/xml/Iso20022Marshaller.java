package ee.tuleva.onboarding.banking.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import java.io.*;
import javax.xml.transform.stream.StreamSource;
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
  public <T> JAXBElement<T> unMarshal(
      String response, Class<T> declaredType, Class<?> objectFactoryClass) {
    JAXBContext unMarshalContext = JAXBContext.newInstance(objectFactoryClass);
    var unmarshaller = unMarshalContext.createUnmarshaller();
    return unmarshaller.unmarshal(new StreamSource(new StringReader(response)), declaredType);
  }
}
