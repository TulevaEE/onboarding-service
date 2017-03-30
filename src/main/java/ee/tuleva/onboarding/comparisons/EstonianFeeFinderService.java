package ee.tuleva.onboarding.comparisons;


import ee.tuleva.onboarding.comparisons.exceptions.ComparisonException;
import ee.tuleva.onboarding.comparisons.exceptions.FeeSizeException;
import ee.tuleva.onboarding.comparisons.exceptions.FundManagerNotFoundException;
import ee.tuleva.onboarding.comparisons.exceptions.SourceHTMLChangedException;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundManager;
import ee.tuleva.onboarding.fund.FundManagerRepository;
import ee.tuleva.onboarding.fund.FundRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Slf4j
@Component
public class EstonianFeeFinderService {

    private static final String feeURLString = "http://www.pensionikeskus.ee/ii-sammas/fondid/fonditasude-vordlused/";

    @Autowired
    private FundManagerRepository fundManagerRepository;

    @Autowired
    private FundRepository fundRepository;


    public void fetchFeesFromPensionSystem(){
        updateFeesFromPensionSystem();
    }

    private void updateFeesFromPensionSystem() {

        try {
            Document doc = Jsoup.connect(feeURLString).get();
            findFundsFromHTML(doc);
        } catch (FundManagerNotFoundException f) {
            log.error("Unrecognized fund manager while scraping pension fund data: " + f.getManagername());
            throw new RuntimeException(f);
        } catch (ParseException | NumberFormatException ne) {
            log.error("Malformed fee or fund code number format while scraping pension fund data");
            throw new RuntimeException(ne);
        } catch (IOException ioe) {
            log.error("Exception while connecting to pension fund system website");
            throw new RuntimeException(ioe);
        } catch (SourceHTMLChangedException shte) {
            log.error("Pensionikeskus website has altered its structure - not able to scrape it for new data.");
            throw new RuntimeException(shte);
        } catch (ComparisonException ce) {
            log.error(ce.getMessage());
            throw new RuntimeException(ce);
        }

    }

    private void findFundsFromHTML(Document doc) throws IOException,ParseException, ComparisonException {

        Elements tables = doc.select("table");
        if (tables == null || tables.isEmpty()) throw new SourceHTMLChangedException();

        Elements allRows = tables.first().select("tr");
        if (allRows == null || allRows.isEmpty()) throw new SourceHTMLChangedException();

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

            Elements links = e.select("a");
            if (links == null || links.isEmpty()) throw new SourceHTMLChangedException();

            String hrefToPensionFundDetails = links.first().attr("href");
            if (hrefToPensionFundDetails == null) throw new SourceHTMLChangedException();

            String[] partsOfPath = hrefToPensionFundDetails.split("/");
            int fundCodeInPensionSystem = Integer.parseInt(partsOfPath[partsOfPath.length - 1]);

            String isin = PensionikeskusCodeToIsin.getIsin(fundCodeInPensionSystem);

            Elements cols = e.select("td");
            if (cols == null || cols.isEmpty()) throw new SourceHTMLChangedException();

            String fundName = cols.get(0).text();
            cols.remove(0);
            String fundmanagerName = fundName.split(" ")[0];


            String tableColValues = cols.select("td").text();
            String[] allFeeValues = tableColValues.split("[^\\d]?%[^\\d]?");

            String managementFee = allFeeValues[allFeeValues.length-1];
            float fee = parseFee(managementFee);

            updateFundRepository(isin, fundName, fundmanagerName, fee);

        }
    }

    private void updateFundRepository(String isin, String fundName, String fundmanagerName, float fee) throws FundManagerNotFoundException {
        FundManager fm = fundManagerRepository.findByName(fundmanagerName);
        if (fm == null) throw new FundManagerNotFoundException(fundmanagerName);
        long fmId = fm.getId();

        FundManager fmm = FundManager.builder().name(fundmanagerName).id(fmId).build();
        Fund f = Fund.builder().isin(isin).managementFeeRate(new BigDecimal(fee)).name(fundName).fundManager(fmm).build();
        if (fundRepository.findByIsin(isin) == null){
            fundRepository.save(f);
        }
    }

    private static float parseFee(String managementFee) throws ComparisonException, ParseException {
        float fee;
        if (managementFee.contains(",")){
            NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
            Number number = format.parse(managementFee);
            fee = number.floatValue()/100;
        }
        else {
            fee = Float.parseFloat(managementFee)/100;
        }
        if (fee > 0.02 || fee < 0) throw new FeeSizeException("Fee size does not match Estonian standards");
        return fee;
    }

}
