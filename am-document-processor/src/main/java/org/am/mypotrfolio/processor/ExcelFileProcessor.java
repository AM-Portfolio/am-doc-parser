package org.am.mypotrfolio.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.*;
import java.util.stream.Collectors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelFileProcessor extends AbstractFileProcessor {

    @Override
    public String getFileType() {
        return "Excel";
    }

    @Override
    public boolean canProcess(String fileExtension) {
        log.debug("Checking if can process file extension: {}", fileExtension);
        return fileExtension != null &&
                (fileExtension.equalsIgnoreCase("xlsx") ||
                        fileExtension.equalsIgnoreCase("xls"));
    }

    @Override
    protected List<Map<String, String>> parseMStockFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 0, 0, 0);
    }

    @Override
    protected List<Map<String, String>> parseZerodhaFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 22, 22, 1);
    }

    @Override
    protected List<Map<String, String>> parseDhanFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 0, 0, 0);
    }

    @Override
    protected List<Map<String, String>> parseGrowFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 20, 20, 0);
    }

    @Override
    protected List<Map<String, String>> parseNseSecurityFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 0, 0, 0);
    }

    @Override
    protected List<Map<String, String>> parseZerodhaTradeFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 14, 14, 0);
    }

    @Override
    protected List<Map<String, String>> parseAngelOneFile(MultipartFile file) throws Exception {
        return parseAngelOneExcelFile(file);
    }

    private List<Map<String, String>> parseAngelOneExcelFile(MultipartFile file) throws Exception {
        List<Map<String, String>> jsonList = new ArrayList<>();
        Workbook workbook = null;
        InputStream inputStream = null;

        try {
            inputStream = file.getInputStream();
            // Try enabling decryption for password protected files
            try {
                workbook = WorkbookFactory.create(inputStream, "JYQPK9320A");
                log.info("Opened workbook with password.");
            } catch (Exception e) {
                log.warn("Failed to open with password, trying without: {}", e.getMessage());
                // If it fails or is not encrypted, try opening normally
                if (inputStream.markSupported()) {
                    inputStream.reset();
                } else {
                    // Re-open stream if reset not supported
                    inputStream.close();
                    inputStream = file.getInputStream();
                }
                workbook = new XSSFWorkbook(inputStream);
            }

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                log.debug("Processing sheet: {}", sheet.getSheetName());

                Iterator<Row> rowIterator = sheet.iterator();
                String currentSection = "";

                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    String firstCell = getCellValueAsString(row.getCell(0));

                    // Detect Section Headers
                    if (firstCell.contains("Equities Details") || firstCell.contains("Equity Holdings Details")) {
                        currentSection = "EQUITY";
                        log.info("Switched to EQUITY section at row {}", row.getRowNum());
                        continue;
                    } else if (firstCell.contains("Mutual Fund Details")) {
                        currentSection = "MF";
                        log.info("Switched to MF section at row {}", row.getRowNum());
                        continue;
                    } else if (firstCell.contains("Bond Details")) {
                        currentSection = "BOND";
                        log.info("Switched to BOND section at row {}", row.getRowNum());
                        continue;
                    }

                    if (currentSection.equals("EQUITY")) {
                        // Equity Structure: [Client ID, Script Name, ISIN, Qty, ...]
                        // Based on decrypted View: ISIN at col 2, Qty at col 3 (0-indexed based on
                        // likely header row)
                        // Validating strictly on ISIN presence
                        Cell isinCell = row.getCell(2);
                        if (isinCell != null) {
                            String isin = getCellValueAsString(isinCell);
                            log.trace("Checking Equity ISIN: {}", isin);
                            if (isin.startsWith("INE")) {
                                String name = getCellValueAsString(row.getCell(1));
                                String quantity = getCellValueAsString(row.getCell(5)); // Col 5
                                String avgPrice = getCellValueAsString(row.getCell(12)); // Col 12
                                String value = getCellValueAsString(row.getCell(15)); // Col 15

                                Map<String, String> rowData = new LinkedHashMap<>();
                                rowData.put("Name", name);
                                rowData.put("Scheme Name", name);
                                rowData.put("ISIN", isin);
                                rowData.put("Quantity", quantity);
                                rowData.put("Units", quantity);
                                rowData.put("Current Value", value);
                                rowData.put("Average Price", avgPrice);
                                jsonList.add(rowData);
                                log.debug("Added Equity: {} ({})", name, isin);
                            }
                        }
                    } else if (currentSection.equals("MF")) {
                        // Mutual Fund Structure: [Client ID, Scheme Name, ISIN, Units, ...]
                        // Based on decrypted View: ISIN at col 2, Units at col 3
                        Cell isinCell = row.getCell(2);
                        if (isinCell != null) {
                            String isin = getCellValueAsString(isinCell);
                            log.trace("Checking MF ISIN: {}", isin);
                            if (isin.startsWith("INF")) {
                                String name = getCellValueAsString(row.getCell(1));
                                String units = getCellValueAsString(row.getCell(3));
                                String nav = getCellValueAsString(row.getCell(4)); // Avg NAV (Cost)
                                String value = getCellValueAsString(row.getCell(7)); // Market Value

                                Map<String, String> rowData = new LinkedHashMap<>();
                                rowData.put("Scheme Name", name);
                                rowData.put("Name", name);
                                rowData.put("ISIN", isin);
                                rowData.put("Units", units);
                                rowData.put("Quantity", units);
                                rowData.put("Current Value", value);
                                rowData.put("NAV", nav);
                                jsonList.add(rowData);
                            }
                        }
                    }
                }
            }
            log.info("Successfully parsed {} rows from Angel One Excel file", jsonList.size());
        } finally {
            if (workbook != null) {
                workbook.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return jsonList;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private List<Map<String, String>> parseExcelFile(MultipartFile file, int headerRow, int skipRows, int skipColumns)
            throws Exception {
        List<Map<String, String>> jsonList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            log.debug("Reading sheet: {}", sheet.getSheetName());
            Iterator<Row> rowIterator = sheet.iterator();

            List<String> headers = new ArrayList<>();
            int rowCount = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() < skipRows)
                    continue;

                if (row.getRowNum() == headerRow) {
                    for (Cell cell : row) {
                        cell.setCellType(CellType.STRING);
                        String header = cell.getStringCellValue().trim();
                        // Skip empty header cells
                        if (header.isEmpty()) {
                            continue;
                        }
                        // Remove BOM if present
                        if (headers.isEmpty() && header.startsWith("\uFEFF")) {
                            header = header.substring(1);
                        }
                        headers.add(header);
                    }
                    // Adjust headers list if we need to skip columns
                    if (skipColumns > 0) {
                        headers = headers.subList(skipColumns, headers.size());
                    }
                    // Filter out any remaining empty headers after processing
                    headers = headers.stream()
                            .filter(h -> h != null && !h.trim().isEmpty())
                            .collect(Collectors.toList());
                    continue;
                }

                if (headers.isEmpty())
                    continue;

                String[] values = new String[headers.size()];
                int valueIndex = 0;
                int cellsToSkip = skipColumns; // Skip first cell + skipColumns
                int cellIndex = 0;

                for (Cell cell : row) {
                    cell.setCellType(CellType.STRING);
                    // Skip first cell and skipColumns
                    if (cellIndex >= cellsToSkip && valueIndex < values.length) {
                        values[valueIndex] = cell.getStringCellValue();
                        valueIndex++;
                    }
                    cellIndex++;
                }

                Map<String, String> rowData = createRowData(headers.toArray(new String[0]), values);
                if (rowData != null) {
                    jsonList.add(rowData);
                    rowCount++;
                }
            }
            log.info("Successfully parsed {} rows from Excel file", rowCount);
        }

        return jsonList;
    }
}
