package com.capco.brsp.synthesisengine.tools;

import com.bazaarvoice.jolt.common.Optional;
import com.bazaarvoice.jolt.modifier.function.Function;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CustomJoltFunctions {
    public static final class timestampToDateString extends Function.SingleFunction<Long> {
        @Override
        protected Optional<Long> applySingle(Object arg1) {
            if (arg1 instanceof String arg1String) {
                var pattern = "EEE, dd MMM yyyy HH:mm:ss Z";

                var dateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                var millis = ZonedDateTime.parse(arg1String, dateTimeFormatter).toInstant().toEpochMilli();
                return Optional.of(millis);
            } else {
                return Optional.empty();
            }
        }
    }

    public static final class timestampToDateStringPattern extends Function.ArgDrivenSingleFunction<String, Long> {
        @Override
        protected Optional<Long> applySingle(String arg1, Object arg2) {
            if (arg1 instanceof String arg1String) {
                var pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
                if (arg2 instanceof String arg2String) {
                    pattern = arg2String;
                }

                var dateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                var millis = ZonedDateTime.parse(arg1String, dateTimeFormatter).toInstant().toEpochMilli();
                return Optional.of(millis);
            } else {
                return Optional.empty();
            }
        }
    }
}
