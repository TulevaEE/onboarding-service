package ee.tuleva.onboarding.holdings;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.GlobalStockIndexRetriever;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.globalstock.ftp.FtpClient;
import ee.tuleva.onboarding.holdings.persistence.HoldingDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingDetailsJob {
    private static final String VAN_ID = "F00000VN9N";
    private static final String PATH = "/Daily/DMRI/XI_MSTAR/";

    private final HoldingDetailsRepository repository;
    private final FtpClient morningstarFtpClient;

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Tallinn") // the top of every day
    public void runJob() {
        downloadHoldingDetails();

    }
    private void downloadHoldingDetails() {

        try{
            morningstarFtpClient.open();
            List<String> fileNames = morningstarFtpClient.listFiles(PATH);

            log.debug("Retrieved list of files: {}", fileNames);

            for (LocalDate date = startDate; date.isBefore(endDate) || date.isEqual(endDate); date = date.plusDays(1)) {
                String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String fileName = fileNames.stream()
                    .filter(string -> string.endsWith(dateString + ".zip"))
                    .findAny()
                    .orElse(null);

                if (fileName == null) {
                    continue;
                }

                try {
                    log.debug("Downloading " + PATH + fileName);
                    InputStream fileStream = morningstarFtpClient.downloadFileStream(PATH + fileName);
                    Optional<GlobalStockIndexRetriever.MonthRecord> optionalRecord = findInZip(fileStream, SECURITY_ID);
                    optionalRecord.ifPresent(monthRecord -> updateMonthRecord(monthRecordMap, monthRecord));
                    fileStream.close();
                    log.debug("Downloaded " + PATH + fileName);

                    log.debug("Waiting for pending commands");
                    morningstarFtpClient.completePendingCommand();
                    log.debug("Finished all pending commands");
                } catch (RuntimeException e) {
                    log.error("Unable to parse file: " + fileName, e);
                }
            }
            URL url = new URL(getDownloadUrlForVan());
            InputStream stream = url.openStream();
            new XmlHoldingDetailParser(stream, VAN_ID, this::persistHoldingDetail).parse();
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        } catch(JAXBException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private void persistHoldingDetail(HoldingDetail detail) {
        repository.save(detail);
    }

    private static class XmlHoldingDetailParser {
        private final InputStream stream;
        private final String vehicleId;
        private final HoldingDetailListener listener;

        private final QName INVESTMENT_VEHICLE = new QName("InvestmentVehicle");
        private final QName ID_ATTRIBUTE = new QName("_Id");
        private final QName HOLDING_DETAIL = new QName("HoldingDetail");

        private boolean isInVehicle = false;
        private Unmarshaller unmarshaller;
        XmlHoldingDetailParser(InputStream stream, String vehicleId, HoldingDetailListener listener) throws JAXBException {
            this.stream = stream;
            this.vehicleId = vehicleId;
            this.listener = listener;

            JAXBContext context = JAXBContext.newInstance(HoldingDetail.class);
            unmarshaller = context.createUnmarshaller();
        }

        private void processStartElement(XMLEventReader reader, StartElement e) throws JAXBException {
            if(e.getName().equals(INVESTMENT_VEHICLE) &&
                e.getAttributeByName(ID_ATTRIBUTE).getValue().equals(vehicleId)) {
                isInVehicle = true;
            } else if(isInVehicle && e.getName().equals(HOLDING_DETAIL)) {
                HoldingDetail holdingDetailEntry =
                    unmarshaller.unmarshal(reader, HoldingDetail.class).getValue();
                listener.onNewHoldingDetail(holdingDetailEntry);
            }
        }

        private void processEndElement(XMLEventReader reader, EndElement e) {
            if(e.getName().equals(INVESTMENT_VEHICLE)) {
                isInVehicle = false;
            }
        }

        public void parse() throws XMLStreamException, JAXBException {
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(stream);
            XMLEvent e = null;

            while((e = xmlEventReader.peek()) != null) {
                if(e.isStartElement()) {
                    processStartElement(xmlEventReader, (StartElement) e);
                } else if(e.isEndElement()) {
                    processEndElement(xmlEventReader, (EndElement) e);
                }
            }
        }
    }

    private interface HoldingDetailListener {
        void onNewHoldingDetail(HoldingDetail detail);
    }
}
