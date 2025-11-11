package com.capco.brsp.synthesisengine.extractors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class CsvExtractor {
    private static final CsvExtractor INSTANCE = new CsvExtractor();

    private CsvExtractor() {
    }

    public static CsvExtractor getInstance() {
        return INSTANCE;
    }

    public static List<List<String>> parseCsv(String csvContent) throws IOException {
        return parseCsv(CSVFormat.DEFAULT, csvContent);
    }

    public static List<List<String>> parseCsv(CSVFormat csvFormat, String csvContent) throws IOException {
        List<List<String>> result = new ArrayList<>();

        try (Reader reader = new StringReader(csvContent);
             CSVParser parser = csvFormat.parse(reader)) {

            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                record.forEach(row::add);
                result.add(row);
            }
        }

        return result;
    }
}
