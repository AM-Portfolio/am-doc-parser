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
        return parseExcelFile(file, 22, 22 , 1);
    }

    @Override
    protected List<Map<String, String>> parseDhanFile(MultipartFile file) throws Exception {
        return parseExcelFile(file, 0, 0 , 0);
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

    private List<Map<String, String>> parseExcelFile(MultipartFile file, int headerRow, int skipRows, int skipColumns) throws Exception {
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
                if (row.getRowNum() < skipRows) continue;

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

                if (headers.isEmpty()) continue;

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
