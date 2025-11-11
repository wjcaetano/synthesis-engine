package com.capco.brsp.synthesisengine.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class XmlUtils {

    private static final XmlUtils INSTANCE = new XmlUtils();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    private static final ObjectWriter XML_PRETTY_WRITER = XML_MAPPER.writerWithDefaultPrettyPrinter();

    private XmlUtils() {
    }

    public static XmlUtils getInstance() {
        return INSTANCE;
    }

    public static boolean isValidXml(String input) {
        try {
            var obj = XML_MAPPER.readValue(input, Object.class);
            return (obj instanceof Map<?, ?> || obj instanceof List<?>);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    public static <T> T readAs(String xml, Class<T> dtoClass) throws JsonProcessingException {
        return XML_MAPPER.readValue(xml, dtoClass);
    }

    public static String writeAsXmlString(Object value, boolean isPretty) {
        try {
            return isPretty ? XML_PRETTY_WRITER.writeValueAsString(value) : XML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to json string!", e);
            return throwableAsXml(e);
        }
    }

    public static String throwableAsXml(Throwable throwable) {
        try {
            Map<String, Object> throwableJson = new ConcurrentLinkedHashMap<>();
            throwableJson.put("error", true);
            throwableJson.put("message", throwable.getMessage());
            throwableJson.put("causeMessage", Utils.nullSafeInvoke(throwable.getCause(), Throwable::getMessage));

            return XML_PRETTY_WRITER.writeValueAsString(throwableJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to create XML from throwable message/causeMessage!", e);
            return "XmlUtils to create XML from throwable message/causeMessage!\n\n" + e.getMessage();
        }
    }
}
