package org.am.mypotrfolio.processor;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class CsvFileProcessor extends AbstractFileProcessor {

    @Override
    public String getFileType() {
        return "CSV";
    }

    @Override
    public boolean canProcess(String fileExtension) {
        log.debug("Checking if can process file extension: {}", fileExtension);
        return fileExtension != null && fileExtension.equalsIgnoreCase("csv");
    }

    @Override
    protected List<Map<String, String>> parseDhanFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 0);
    }

    @Override
    protected List<Map<String, String>> parseMStockFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 0);
    }

    @Override
    protected List<Map<String, String>> parseZerodhaFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 22);
    }

    @Override
    protected List<Map<String, String>> parseGrowFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 20);
    }

    @Override
    protected List<Map<String, String>> parseNseSecurityFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 0);
    }
    @Override
    protected List<Map<String, String>> parseZerodhaTradeFile(MultipartFile file) throws Exception {
        return parseCsvFile(file, 15);
    }

    private List<Map<String, String>> parseCsvFile(MultipartFile file, int skipLines) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .withSkipLines(skipLines)
                .build()) {

            String[] headers = reader.readNext();
            if (headers != null) {
                // Remove BOM from the first header if present
                if (headers.length > 0 && headers[0].startsWith("\uFEFF")) {
                    headers[0] = headers[0].substring(1);
                }
                log.debug("Found headers: {}", Arrays.toString(headers));
                String[] line;
                int rowCount = 0;
                while ((line = reader.readNext()) != null) {
                    Map<String, String> row = createRowData(headers, line);
                    if (row != null) {
                        data.add(row);
                        rowCount++;
                    }
                }
                log.info("Successfully processed {} rows from CSV file", rowCount);
            } else {
                log.warn("No headers found in CSV file");
            }
        }
        return data;
    }
}
