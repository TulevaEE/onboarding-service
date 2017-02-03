package ee.tuleva.onboarding.comparisons;


import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundManager;
import ee.tuleva.domain.fund.FundManagerRepository;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.comparisons.exceptions.FeeSizeException;
import ee.tuleva.onboarding.comparisons.exceptions.FundManagerNameException;
import ee.tuleva.onboarding.comparisons.exceptions.IsinNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

@Slf4j
@Component
public class EstonianFeeFinderService {

    private static final String feeURLString = "http://www.pensionikeskus.ee/ii-sammas/fondid/fonditasude-vordlused/";

    @Autowired
    private FundManagerRepository fundManagerRepository;

    @Autowired
    private FundRepository fundRepository;

    //TODO: schedule after every 3 months
    //@Scheduled(cron = "0 0 0 1 */3 ?")
    @Scheduled(fixedDelay = 300000)
    public void fetchFeesFromPensionSystem(){
        updateFeesFromPensionSystem();
    }

    public void updateFeesFromPensionSystem() {

        try {
            Document doc = Jsoup.connect(feeURLString).get();
            findFundsFromHTML(doc);
        } catch (FundManagerNameException f){
            log.error("Unrecognized fund manager while scraping pension fund data: "+f.getManagername());
        } catch (ParseException p){
            log.error("Malformed number format while scraping pension fund data");
        } catch (IOException ioe){
            log.error("Exception while connecting to pension fund system website");
        } catch (FeeSizeException fe){
            log.error("Fee size does not match Estonian standards");
        }

    }

    protected void findFundsFromHTML(Document doc) throws ParseException, FeeSizeException, FundManagerNameException{

        Element table = doc.select("table").get(0);
        Elements allRows = table.select("tr");
        Elements rows = new Elements();

        for (Element e : allRows) {
            Attributes rowAttributes = e.attributes();
            for (Attribute attribute : rowAttributes) {
                if (attribute.getValue().equals("data")) {
                    rows.add(e);
                }
            }
        }

        for (Element e : rows) {

            String hrefToPensionFundDetails = e.select("a").first().attr("href");
            String[] partsOfPath = hrefToPensionFundDetails.split("/");
            int fundCodeInPensionSystem = Integer.parseInt(partsOfPath[partsOfPath.length - 1]);

            String isin = PensionFundSystemCodeToIsinMap.getIsin(fundCodeInPensionSystem);

            Elements cols = e.select("td");
            String fundName = cols.get(0).text();
            cols.remove(0);
            String tableColValues = cols.select("td").text();
            String[] allFeeValues = tableColValues.split("[^\\d]?%[^\\d]?");

            String managementFee = allFeeValues[allFeeValues.length-1];
            float fee = parseFee(managementFee);

            String fundmanagerName = fundName.split(" ")[0];

            FundManager fm = fundManagerRepository.findByName(fundmanagerName);

            if (fm == null) throw new FundManagerNameException(fundmanagerName);

            long fmId = fm.getId();
            FundManager fmm = FundManager.builder().name(fundmanagerName).id(fmId).build();
            Fund f = Fund.builder().isin(isin).managementFeeRate(new BigDecimal(fee)).name(fundName).fundManager(fmm).build();
            if (fundRepository.findByIsin(isin) == null){
                fundRepository.save(f);
            }

        }
    }

    private static float parseFee(String managementFee) throws ParseException, FeeSizeException {
        float fee;
        if (managementFee.contains(",")){
            NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
            Number number = format.parse(managementFee);
            fee = number.floatValue()/100;
        }
        else {
            fee = Float.parseFloat(managementFee)/100;
        }

        if (fee > 0.02 || fee < 0) throw new FeeSizeException();
        return fee;
    }

}
