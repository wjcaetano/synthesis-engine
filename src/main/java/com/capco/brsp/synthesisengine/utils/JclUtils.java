package com.capco.brsp.synthesisengine.utils;

import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JclUtils {
    private static final JclUtils INSTANCE = new JclUtils();

    private JclUtils() {
    }

    public static JclUtils getInstance() {
        return INSTANCE;
    }

    public static JclBasic parse(String content) {
        String usefullContent = content.replaceAll("^/\\*[\\s\\S]*?\\*/", "");
        List<String> contentLines = Arrays.stream(usefullContent.split("\\r*\\n")).toList();
        List<String> usefullLines = contentLines.stream().filter(line -> !line.startsWith("//*")).toList();
        usefullContent = usefullLines.stream()
                .map(it -> Utils.getTextBefore(it, "//\\*").stripTrailing())
                .collect(Collectors.joining("\n"));
        usefullContent = Utils.removeColumns(usefullContent, 0, 72);

        var name = Utils.getRegexFirstNotNullGroup(usefullContent, "^//(\\w+)[\\s\\S]+");
        var type = Utils.getRegexFirstNotNullGroup(usefullContent, "^//\\w+\\s+(JOB|PROC)[\\s\\S]+");
        var setOfExecs = getSet(usefullContent, "\\bEXEC\\s+(?:PGM\\s*=|PROC\\s*=)?\\s*(\\w+)|//(?:SYSIN|SYSTSIN)\\s+DD\\s+DSN=[^\\(]+\\((\\w+)\\)");
        var setOfProcProgs = getSet(usefullContent, "\\s+PROC[\\s\\S]+PROG\\s*=\\s*(\\w+)");
        var setOfCtcs = getSet(usefullContent, "&CTLLIB\\((\\w+)\\)");
        var setOfRuns = getSet(usefullContent, "\\bRUN\\s+PROGRAM\\((\\w+)\\)");

        return JclBasic.builder()
                .name(name)
                .type(type)
                .rawCode(content)
                .execs(Sets.union(setOfExecs, setOfProcProgs))
                .ctcs(setOfCtcs)
                .instreamRuns(setOfRuns)
                .build();
    }

    public static Set<String> getSet(String content, String regex) {
        Set<String> set = new LinkedHashSet<>();

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                var group = matcher.group(i);
                if (group != null) {
                    set.add(group);
                }
            }
        }

        return set;
    }
}
