package ee.tuleva.onboarding.holdings.adapters;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class XmlDateAdapter extends XmlAdapter<String, LocalDate> {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDate unmarshal(String xml){
        return LocalDate.parse(xml, formatter);
    }

    @Override
    public String marshal(LocalDate object){
        return object.format(formatter);
    }
}