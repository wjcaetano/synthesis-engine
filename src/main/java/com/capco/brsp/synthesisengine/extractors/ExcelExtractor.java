package com.capco.brsp.synthesisengine.extractors;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList;
import com.capco.brsp.synthesisengine.utils.Utils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.odftoolkit.simple.SpreadsheetDocument;

import java.io.*;
import java.util.List;
import java.util.Map;

public class ExcelExtractor {
    private static final ExcelExtractor INSTANCE = new ExcelExtractor();

    private ExcelExtractor() {
    }

    public static ExcelExtractor getInstance() {
        return INSTANCE;
    }

    public static Map<String, Map<String, List<List<Object>>>> extractFromODSBase64(String fileBase64) throws IOException {
        byte[] fileBytes = Utils.decodeBase64(fileBase64);

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return extractFromODS("Workbook", inputStream);
        }
    }

    public static Map<String, Map<String, List<List<Object>>>> extractFromODS(File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return extractFromODS(file.getName(), inputStream);
        }
    }

    public static Map<String, Map<String, List<List<Object>>>> extractFromODS(String fileName, InputStream inputStream) {
        try {
            if (fileName == null) {
                fileName = "Workbook";
            }

            SpreadsheetDocument odsDoc = SpreadsheetDocument.loadDocument(inputStream);

            Map<String, Map<String, List<List<Object>>>> excelObject = new ConcurrentLinkedHashMap<>();
            Map<String, List<List<Object>>> sheetsMap = new ConcurrentLinkedHashMap<>();
            excelObject.put(fileName, sheetsMap);

            for (org.odftoolkit.simple.table.Table table : odsDoc.getTableList()) {
                List<List<Object>> sheetRows = new ConcurrentLinkedList<>();
                sheetsMap.put(table.getTableName(), sheetRows);

                for (org.odftoolkit.simple.table.Row row : table.getRowList()) {
                    List<Object> rowCells = new ConcurrentLinkedList<>();
                    sheetRows.add(rowCells);

                    int maxColumns = 200;
                    int emptyStreak = 0;

                    for (int i = 0; i < maxColumns; i++) {
                        org.odftoolkit.simple.table.Cell cell = row.getCellByIndex(i);
                        String text = cell != null ? cell.getDisplayText() : "";

                        if (text == null || text.isBlank()) {
                            emptyStreak++;
                            if (emptyStreak >= 20) break;
                        } else {
                            emptyStreak = 0;
                        }

                        rowCells.add(text);
                    }
                }
            }

            return excelObject;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ODS file", e);
        }
    }

    public static Map<String, Map<String, List<List<Object>>>> extractTextFromExcelBase64(String fileBase64) throws IOException {
        byte[] fileBytes = Utils.decodeBase64(fileBase64);

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return extractTextFromExcel("Workbook", inputStream);
        }
    }

    public static Map<String, Map<String, List<List<Object>>>> extractTextFromExcel(File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return extractTextFromExcel(file.getName(), inputStream);
        }
    }

    public static Map<String, Map<String, List<List<Object>>>> extractTextFromExcel(String fileName, InputStream inputStream) throws IOException {
        String workbookName = Utils.nvl(fileName, "Workbook");

        try {
            // Generic
            try (Workbook workbook = WorkbookFactory.create(inputStream)) {
                return extractTextFromExcel(workbookName, workbook);
            }
        } catch (Exception ex) {
            try {
                // .xslx
                try (Workbook workbook = new XSSFWorkbook(inputStream)) {
                    return extractTextFromExcel(workbookName, workbook);
                }
            } catch (Exception ex2) {
                // .xls
                try (Workbook workbook = new HSSFWorkbook(inputStream)) {
                    return extractTextFromExcel(workbookName, workbook);
                }
            }
        }
    }

    private static Map<String, Map<String, List<List<Object>>>> extractTextFromExcel(String workbookName, Workbook workbook) throws IOException {
        Map<String, Map<String, List<List<Object>>>> excelObject = new ConcurrentLinkedHashMap<>();

        Map<String, List<List<Object>>> sheetsMap = new ConcurrentLinkedHashMap<>();
        excelObject.put(workbookName, sheetsMap);

        for (Sheet sheet : workbook) {
            List<List<Object>> sheetRows = new ConcurrentLinkedList<>();
            sheetsMap.put(sheet.getSheetName(), sheetRows);

            for (Row row : sheet) {
                List<Object> rowCells = new ConcurrentLinkedList<>();
                sheetRows.add(rowCells);

                for (Cell cell : row) {
                    rowCells.add(getCellValue(cell));
                }
            }
        }

        return excelObject;
    }

    private static String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
