package ee.tuleva.onboarding.swedbank.http;

import jakarta.xml.bind.JAXBContext;
import java.io.*;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
class SwedbankGatewayMarshaller {

  @SneakyThrows
  public String marshalToString(Object object) {
    JAXBContext marshalContext =
        JAXBContext.newInstance(ee.swedbank.gateway.iso.request.ObjectFactory.class);
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
