package com.capco.brsp.synthesisengine.configuration;

import com.capco.brsp.synthesisengine.extractors.CsvExtractor;
import com.capco.brsp.synthesisengine.extractors.ExcelExtractor;
import com.capco.brsp.synthesisengine.extractors.Extractors;
import com.capco.brsp.synthesisengine.extractors.HtmlExtractor;
import com.capco.brsp.synthesisengine.utils.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilsConfig {
    @Value("${configs.includeFileContentOnProgressResponse}")
    public boolean includeFileContentOnProgressResponse = false;

    @Bean(name = "CsvExtractor")
    public CsvExtractor csvExtractor() {
        return CsvExtractor.getInstance();
    }

    @Bean(name = "ExcelExtractor")
    public ExcelExtractor excelExtractor() {
        return ExcelExtractor.getInstance();
    }

    @Bean(name = "HtmlExtractor")
    public HtmlExtractor htmlExtractor() {
        return HtmlExtractor.getInstance();
    }

    @Bean(name = "Extractors")
    public Extractors extractors() {
        return Extractors.getInstance();
    }

    @Bean(name = "ParserUtils")
    public ParserUtils parserUtils() {
        return ParserUtils.getInstance();
    }

    @Bean(name = "JavaUtils")
    public JavaUtils javaUtils() {
        return JavaUtils.getInstance();
    }

    @Bean(name = "COBOLUtils")
    public COBOLUtils COBOLUtils() {
        return COBOLUtils.getInstance();
    }

    @Bean(name = "JclUtils")
    public JclUtils JclUtils() {
        return JclUtils.getInstance();
    }

    @Bean(name = "SuperUtils")
    public SuperUtils superUtils() {
        return SuperUtils.getInstance();
    }

    @Bean(name = "Utils")
    public Utils utils() {
        return Utils.getInstance();
    }

    @Bean(name = "WebSearchUtils")
    public WebSearchUtils webSearchUtils() {
        return WebSearchUtils.getInstance();
    }

    @Bean(name = "FileUtils")
    public FileUtils fileUtils() {
        return FileUtils.getINSTANCE();
    }

    @Bean(name = "JsonUtils")
    public JsonUtils jsonUtils() {
        return JsonUtils.getInstance();
    }

    @Bean(name = "XmlUtils")
    public XmlUtils xmlUtils() {
        return XmlUtils.getInstance();
    }

    @Bean(name = "YamlUtils")
    public YamlUtils yamlUtils() {
        return YamlUtils.getInstance();
    }

    @Bean(name = "MarkdownUtils")
    public MarkdownUtils markdownUtils() {
        return MarkdownUtils.getInstance();
    }
}