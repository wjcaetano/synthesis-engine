package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.dto.FileDto;
import com.capco.brsp.synthesisengine.dto.TransformDto;
import com.capco.brsp.synthesisengine.dto.YamlActionItem;
import com.capco.brsp.synthesisengine.enums.EnumYamlActionItemType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import groovy.util.logging.Slf4j;
import org.aspectj.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@lombok.extern.slf4j.Slf4j
@Slf4j
public class JavaUtils {
    private static final JavaUtils INSTANCE = new JavaUtils();

    private JavaUtils() {
    }

    public static JavaUtils getInstance() {
        return INSTANCE;
    }

    public static String normalizeJavaIdentifier(String name) {
        return Pattern.compile("[a-zA-Z0-9]+")
                .matcher(name)
                .results()
                .map(matchResult -> matchResult.group().toLowerCase())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(""));
    }

    public static String extractAndWriteOtherClasses(String microservicePath, String mainFilePath, String content) {
        Pattern pattern = Pattern.compile("```.*?\n([\\s\\S]*)```");
        Matcher matcher = pattern.matcher(content);

        String mainContent = content;
        int matchCount = 0;
        while (matcher.find()) {
            matchCount++;
        }

        matcher.reset();
        while (matcher.find()) {
            String match = matcher.group(1);
            if (matchCount == 1 || mainContent.contains(mainFilePath)) {
                mainContent = match;
            } else {
                String filePath = Utils.getRegexGroup(match, "^//\\s*(.*)\\s*", 1);
                Path fullFilePath = FileUtils.pathJoin(microservicePath, filePath);
                FileUtils.writeFile(fullFilePath, match, false);
            }
        }

        return mainContent;
    }

    public static String withoutClass(String javaCode) {
        var regex = "([\\s\\S]*?)public\\s+class\\s+\\w+\\s+\\{([\\s\\S]*?)}\\s*$";

        if (javaCode.matches(regex)) {
            var importsRegex = "^import\\s+.*?;\\s*";
            Pattern pattern = Pattern.compile(importsRegex, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(javaCode);
            List<String> imports = new ConcurrentLinkedList<>();
            while (matcher.find()) {
                String item = matcher.group();
                imports.add(item);
            }

            var importsString = String.join("\n", imports);
            var classContentRegex = "(public|private)\\s+class\\s+\\w+\\s+\\{([\\s\\S]*?)}\\s*$";
            var content = Utils.getRegexGroup(javaCode, classContentRegex, 2);
            return importsString + "\n" + content;
        }

        return javaCode;
    }

//    public class MethodStruct {
//        private String name;
//        private String access; // private|public
//        private String calledFrom;
//        private String callingTo;
//        private String rawCode;
//        private String signature;

    public static Set<String> getAllUndeclaredVariables(String content, String... ignore) {
        Set<String> undeclaredVars = new LinkedHashSet<>();
        List<String> listToIgnore = Arrays.stream(ignore).toList();

        CompilationUnit cu = StaticJavaParser.parse(content);

        Optional<ClassOrInterfaceDeclaration> clazzOpt = cu.getClassByName(
                cu.findFirst(ClassOrInterfaceDeclaration.class).map(ClassOrInterfaceDeclaration::getNameAsString).orElse("")
        );

        if (clazzOpt.isEmpty()) {
            log.info("No class found.");
            return undeclaredVars;
        }

        ClassOrInterfaceDeclaration clazz = clazzOpt.get();

        Set<String> classFields = clazz.findAll(FieldDeclaration.class).stream()
                .flatMap(field -> field.getVariables().stream())
                .map(NodeWithSimpleName::getNameAsString)
                .collect(Collectors.toSet());

        clazz.findAll(MethodDeclaration.class).forEach(method -> {
            Set<String> declaredVars = new HashSet<>();

            for (Parameter p : method.getParameters()) {
                declaredVars.add(p.getNameAsString());
            }

            method.findAll(VariableDeclarator.class).forEach(v -> declaredVars.add(v.getNameAsString()));

            List<String> usedNames = method.findAll(NameExpr.class).stream()
                    .map(NameExpr::getNameAsString)
                    .toList();

            List<String> undeclared = usedNames.stream()
                    .filter(name -> Character.isLowerCase(name.charAt(0)) && !listToIgnore.contains(name) && !declaredVars.contains(name) && !classFields.contains(name))
                    .distinct()
                    .toList();

            undeclaredVars.addAll(undeclared);
        });

        return undeclaredVars;
    }

    public static Map<String, FieldDeclaration> getAllFieldVariablesMap(String code) {
        Map<String, FieldDeclaration> allVariableNames = new ConcurrentLinkedHashMap<>();

        CompilationUnit cu = StaticJavaParser.parse(code);

        Optional<ClassOrInterfaceDeclaration> classOpt = cu.findFirst(ClassOrInterfaceDeclaration.class);

        if (classOpt.isPresent()) {
            ClassOrInterfaceDeclaration clazz = classOpt.get();

            // Get all field (class-level variable) declarations
            List<FieldDeclaration> fields = clazz.getFields();

            fields.forEach(it -> it.getVariables().forEach(it2 -> allVariableNames.put(it2.getNameAsString(), it)));
        } else {
            code = "public class FakeClass {\n" + code + "\n}";
            allVariableNames.putAll(getAllFieldVariablesMap(code));
        }

        return allVariableNames;
    }

    public static String addAllFieldsToClass(String code, Set<FieldDeclaration> fieldDeclarations) {
        var parsedCode = StaticJavaParser.parse(code);
        var javaClass = parsedCode.findAll(ClassOrInterfaceDeclaration.class).stream().findFirst().orElse(null);
        fieldDeclarations.forEach(it -> javaClass.addMember(it));

        return javaClass.toString();
    }

    public static Map<String, String> extractMethodsFromCode(String content) {
        List<String> allCodes = Utils.getAllRegexGroup(content, "```[^\\n]*\\n?([\\s\\S]+?)\\n```", 1);

        Map<String, String> methods = new ConcurrentLinkedHashMap<>();

        allCodes.forEach(it -> {
            var map = extractMethodsFromCode(content, null);
            var imports = map.remove("$imports");
            if (imports != null) {
                methods.put("$imports", methods.get("$imports") + "\n" + imports);
            }
            methods.putAll(map);
        });

        return methods;
    }

    public static Map<String, String> extractMethodsFromCode(String content, String methodName) {
        String javaCode = (String) Utils.extractMarkdownCode(content);

        StringBuilder imports = new StringBuilder();
        StringBuilder javaCodeMethods = new StringBuilder();
        javaCode.lines().forEach(it -> {
            if (it.trim().startsWith("import")) {
                imports.append(it).append("\n");
            } else {
                javaCodeMethods.append(it).append("\n");
            }
        });

        javaCode = javaCodeMethods.toString();

        Map<String, String> methodsMap = new ConcurrentLinkedHashMap<>();
        try {
            MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(javaCode);
            if (methodName == null || methodName.equals(method.getNameAsString())) {
                methodsMap.put(method.getNameAsString(), method.toString());
            }
        } catch (ParseProblemException e) {
            CompilationUnit parsed;
            try {
                parsed = StaticJavaParser.parse(javaCode);
            } catch (ParseProblemException e2) {
                String rearrangedJavaCode = imports + "\npublic class FakeClass {\n" + javaCodeMethods + "\n}";
                parsed = StaticJavaParser.parse(rearrangedJavaCode);
            }

            var allMethods = parsed.findAll(MethodDeclaration.class).stream().toList();

            var mainMethod = allMethods.stream().filter(it -> methodName == null || it.getNameAsString().equals(methodName)).findFirst().orElse(null);
            if (mainMethod == null) {
                return methodsMap;
            }

            methodsMap.put(mainMethod.getNameAsString(), mainMethod.toString());

            mainMethod.findAll(MethodCallExpr.class)
                    .stream().map(MethodCallExpr::getNameAsString)
                    .distinct()
                    .forEach(calledMethodName -> allMethods.stream()
                            .filter(oneMethod -> oneMethod.getNameAsString().equals(calledMethodName))
                            .findFirst()
                            .ifPresent(calledMethod -> methodsMap.put(calledMethod.getNameAsString(), calledMethod.toString())));
        }

        if (!imports.isEmpty()) {
            methodsMap.put("$imports", imports.toString());
        }

        return methodsMap;
    }

    public static String extractMethodFromCode(String content, String methodName) {
        String javaCode = (String) Utils.extractMarkdownCode(content);

        try {
            MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(javaCode);
            if (methodName.equals(method.getNameAsString())) {
                return method.toString();
            }
        } catch (ParseProblemException e) {
            CompilationUnit parsed;
            StringBuilder imports = new StringBuilder();
            try {
                parsed = StaticJavaParser.parse(javaCode);
            } catch (ParseProblemException e2) {
                StringBuilder javaCodeMethods = new StringBuilder();
                javaCode.lines().forEach(it -> {
                    if (it.trim().startsWith("import")) {
                        imports.append(it).append("\n");
                    } else {
                        javaCodeMethods.append(it).append("\n");
                    }
                });


                String rearrangedJavaCode = imports + "\npublic class FakeClass {\n" + javaCodeMethods + "\n}";
                parsed = StaticJavaParser.parse(rearrangedJavaCode);
            }

            var allMethods = parsed.findAll(MethodDeclaration.class).stream().toList();

            var mainMethod = allMethods.stream().filter(it -> it.getNameAsString().equals(methodName)).findFirst().orElse(null);
            if (mainMethod == null) {
                return null;
            }

            StringBuilder allMethodsContent = new StringBuilder(mainMethod.toString());

            mainMethod.findAll(MethodCallExpr.class)
                    .stream().map(MethodCallExpr::getNameAsString)
                    .distinct()
                    .forEach(calledMethodName -> allMethods.stream()
                            .filter(oneMethod -> oneMethod.getNameAsString().equals(calledMethodName))
                            .findFirst()
                            .ifPresent(calledMethod -> allMethodsContent.append("\n\n").append(calledMethod)));

            return imports.isEmpty() ? allMethodsContent.toString() : imports + "\n" + allMethodsContent;
        }

        return null;
    }

    public static List<String> listAllMethods(String javaCode) {
        List<String> list = new ConcurrentLinkedList<>();

        try {
            JavaParser javaParser = new JavaParser();
            CompilationUnit compilationUnit = javaParser.parse(javaCode).getResult().get();

            ClassOrInterfaceDeclaration classDecl = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream().findFirst().orElse(null);
            if (classDecl == null) {
                return list;
            }

            List<MethodDeclaration> methods = classDecl.getMethods();
            for (MethodDeclaration method : methods) {

                int startIndex = calculateOffset(javaCode, method.getBegin().get());
                int endIndex = calculateOffset(javaCode, method.getEnd().get());
                String methodSubString = javaCode.substring(startIndex, endIndex + 1);
                list.add(methodSubString);

            }
        } catch (Exception ex) {
            log.error("Couldn't check for a list of methods!");
            ex.printStackTrace();
        }

        return list;
    }

    public static String getClassName(String javaCode) {
        try {
            JavaParser javaParser = new JavaParser();
            CompilationUnit compilationUnit = javaParser.parse(javaCode).getResult().get();

            ClassOrInterfaceDeclaration classDecl = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream().findFirst().orElse(null);

            return classDecl.getName().getIdentifier();
        } catch (Exception ex) {
            log.error("Couldn't find a class!");
            ex.printStackTrace();
        }

        return null;
    }

    public static List<String> listPoorMethods(String javaCode) {
        List<String> list = new ConcurrentLinkedList<>();

        JavaParser javaParser = new JavaParser();
        CompilationUnit compilationUnit = javaParser.parse(javaCode).getResult().get();

        ClassOrInterfaceDeclaration classDecl = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream().findFirst().orElse(null);
        if (classDecl == null) {
            return list;
        }

        List<MethodDeclaration> methods = classDecl.getMethods();
        for (MethodDeclaration method : methods) {
            if (method.getAllContainedComments().stream().anyMatch(it -> it.getContent().matches("(?i).*\\bTODO\\b.*"))) {
                int startIndex = calculateOffset(javaCode, method.getBegin().get());
                int endIndex = calculateOffset(javaCode, method.getEnd().get());
                String methodSubString = javaCode.substring(startIndex, endIndex + 1);
                list.add(methodSubString);
            }
        }

        return list;
    }

    private static int calculateOffset(String code, Position position) {
        int offset = 0;
        String[] lines = code.split("\n");

        for (int i = 0; i < position.line - 1; i++) {
            offset += lines[i].length() + 1;
        }

        offset += position.column - 1;
        return offset;
    }

    public static void operate(FileDto fileDto, String rootFolder, String content) throws Exception {
        var mapOfYamlActions = YamlUtils.readYAMLContentAsMap(content, YamlActionItem.class);

        Map<String, String> fileContentCache = new ConcurrentLinkedHashMap<>();

        Set<String> keySet = new LinkedHashSet<>(mapOfYamlActions.keySet());
        for (String actionName : keySet) {
            YamlActionItem yamlActionItem = mapOfYamlActions.remove(actionName);

            String targetPath = yamlActionItem.getTargetPath();
            if (targetPath.contains("!")) {
                String relativeFilePath = targetPath.split("!")[0];
                String astPath = targetPath.split("!")[1];

                String filePath = FileUtils.absolutePathJoin(rootFolder, relativeFilePath).toString();

                File javaFile = new File(filePath);
                if (!javaFile.exists()) {
                    throw new FileNotFoundException("Didn't found the file!");
                }
                String javaFileContent = fileContentCache.computeIfAbsent(filePath, k -> {
                    try {
                        return FileUtil.readAsString(javaFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                CompilationUnit cu = StaticJavaParser.parse(javaFileContent);
                Node targetNode = resolveAstPath(cu, astPath);

                if (targetNode == null) {
                    System.err.println("Target not found: " + astPath);
                    return;
                }

                switch (yamlActionItem.getAction()) {
                    case INCLUDE -> applyInclude(targetNode, yamlActionItem.getContent());
                    case REMOVE -> applyRemove(targetNode);
                    case REPLACE -> applyReplace(targetNode, yamlActionItem.getContent());
                }

                fileContentCache.put(filePath, cu.toString());
            } else if (targetPath.contains("@") || yamlActionItem.getFirstLine() != null) {
                String relativeFilePath = targetPath;
                if (yamlActionItem.getFirstLine() == null) {
                    relativeFilePath = targetPath.split("@")[0];
                    String lineRange = targetPath.split("@")[1];

                    String[] lineRangeStrings = lineRange.split(",");
                    int lineStart = Integer.parseInt(lineRangeStrings[0]);
                    int lineStop = lineStart;
                    if (lineRangeStrings.length > 1) {
                        lineStop = Integer.parseInt(lineRangeStrings[1]);
                    }

                    yamlActionItem.setFirstLine(lineStart);
                    yamlActionItem.setLastLine(lineStop);
                }

                String filePath = FileUtils.absolutePathJoin(rootFolder, relativeFilePath).toString();

                File javaFile = new File(filePath);
                if (!javaFile.exists()) {
                    throw new FileNotFoundException("Didn't found the file: " + filePath);
                }
                String javaFileContent = fileContentCache.computeIfAbsent(filePath, k -> {
                    try {
                        return FileUtil.readAsString(javaFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                String yamlActionItemContent = yamlActionItem.getContent();
                var adjustedContent = applyActionByLineRange(mapOfYamlActions, yamlActionItem.getAction(), javaFileContent, yamlActionItem.getFirstLine(), yamlActionItem.getLastLine(), yamlActionItemContent);
                fileContentCache.put(filePath, adjustedContent);
            }
        }

        final Map<String, String> formatted = new ConcurrentLinkedHashMap<>();
        fileContentCache.forEach((k, v) -> {
            String formattedValue = v;
            try {
                formattedValue = StaticJavaParser.parse(v).toString();
            } catch (Exception ex) {
                log.error("Failed to parse the fixed code! Error message: {}", ex.getMessage());
            }

            formatted.put(k, formattedValue);
        });

        formatted.forEach((k, v) -> {
            Path newFilePath = new File(k).toPath();
            FileUtils.writeFile(newFilePath, v, false);
            fileDto.setContent(v);

            var fileDtoHistory = fileDto.getHistory();
            String indexedAgentName = "Agent " + fileDtoHistory.stream().filter(it -> it.getName().startsWith("Agent")).count() / 2;

            fileDtoHistory.add(
                    TransformDto.builder()
                            .name(indexedAgentName + " Requested")
                            .content(content)
                            .build());
            fileDtoHistory.add(
                    TransformDto.builder()
                            .name(indexedAgentName + " Solved")
                            .content(v)
                            .build());
            log.info("Patch applied and file saved: {}", newFilePath);
        });
    }

    private static String applyActionByLineRange(Map<String, YamlActionItem> mapOfYamlActions, EnumYamlActionItemType enumYamlActionItemType, String javaFileContent, Integer firstLine, Integer lastLine, String newContent) {
        return switch (enumYamlActionItemType) {
            case INCLUDE -> {
                int inclusionLines = (int) newContent.lines().count();
                mapOfYamlActions.forEach((k, v) -> {
                    if (v.getFirstLine() >= firstLine) {
                        v.setFirstLine(v.getFirstLine() + inclusionLines);
                        v.setLastLine(v.getLastLine() + inclusionLines);
                    }
                });
                yield insertLineAt(javaFileContent, firstLine, newContent.stripTrailing());
            }
            case REMOVE -> {
                mapOfYamlActions.forEach((k, v) -> {
                    if (v.getFirstLine() >= firstLine) {
                        v.setFirstLine(v.getFirstLine() - (lastLine - firstLine + 1));
                        v.setLastLine(v.getLastLine() - (lastLine - firstLine + 1));
                    }
                });
                yield removeLinesInRange(javaFileContent, firstLine, lastLine);
            }
            case REPLACE -> {
                long sizeOfOldContent = (lastLine - firstLine + 1);
                int lineAdjusment = (int) (newContent.lines().count() - sizeOfOldContent);
                mapOfYamlActions.forEach((k, v) -> {
                    if (v.getFirstLine() >= firstLine) {
                        v.setFirstLine(v.getFirstLine() + lineAdjusment);
                        v.setLastLine(v.getLastLine() + lineAdjusment);
                    }
                });
                yield replaceLines(javaFileContent, firstLine, lastLine, newContent.stripTrailing());
            }
            default -> javaFileContent;
        };
    }

    public static String insertLineAt(String input, int lineNumber, String contentToInsert) {
        String[] lines = input.split("\n", -1);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i <= lines.length; i++) {
            if (i == lineNumber - 1) {
                result.append(contentToInsert).append("\n");
            }
            if (i < lines.length) {
                result.append(lines[i]);
                if (i < lines.length - 1 || (i == lines.length - 1 && !input.endsWith("\n"))) {
                    result.append("\n");
                }
            }
        }

        return result.toString();
    }

    public static String removeLinesInRange(String input, int fromLine, int toLine) {
        String[] lines = input.split("\n", -1);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1; // lines are 1-based
            if (lineNum < fromLine || lineNum > toLine) {
                result.append(lines[i]);
                if (i < lines.length - 1) {
                    result.append("\n");
                }
            }
        }

        return result.toString();
    }

    public static String replaceLines(String originalText, int startLine, int endLine, String newContent) {
        String[] lines = originalText.split("\r?\n", -1); // Preserve empty lines
        StringBuilder result = new StringBuilder();

        if (startLine < 1 || endLine > lines.length || startLine > endLine) {
            throw new IllegalArgumentException("Invalid line range: " + startLine + " to " + endLine);
        }

        for (int i = 0; i < startLine - 1; i++) {
            result.append(lines[i]).append("\n");
        }

        if (!newContent.isEmpty()) {
            result.append(newContent);
            if (!newContent.endsWith("\n")) {
                result.append("\n");
            }
        }

        for (int i = endLine; i < lines.length; i++) {
            result.append(lines[i]);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    private static Node resolveAstPath(Node root, String targetPath) {
        String[] parts = targetPath.split("\\.");
        List<? extends Node> currentNodes = List.of(root);

        for (String part : parts) {
            String[] typeAndId = part.split(":");
            String nodeType = typeAndId[0];
            String identifier = typeAndId.length > 1 ? typeAndId[1] : null;

            var nextNodes = currentNodes.stream()
                    .flatMap(node -> findChildrenByTypeAndName(node, nodeType, identifier).stream())
                    .toList();

            if (nextNodes.isEmpty()) return null;

            currentNodes = nextNodes;
        }

        return currentNodes.getFirst();
    }

    private static <T extends Node> Stream<T> findChildOrAll(Node parent, Class<T> type) {
        List<T> directList = parent.getChildNodes().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();

        if (!directList.isEmpty()) {
            return directList.stream();
        }

        return parent.findAll(type).stream();
    }

    private static List<? extends Node> findChildrenByTypeAndName(Node parent, String nodeType, String identifier) {
        return switch (nodeType) {
            case "ClassOrInterfaceDeclaration" ->
                    findChildOrAll(parent, ClassOrInterfaceDeclaration.class).filter(n -> identifier == null || n.getNameAsString().equals(identifier)).toList();
            case "MethodDeclaration" ->
                    parent.findAll(MethodDeclaration.class).stream().filter(n -> identifier == null || n.getNameAsString().equals(identifier)).toList();
            case "FieldDeclaration" ->
                    parent.findAll(FieldDeclaration.class).stream().filter(n -> identifier == null || n.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(identifier))).toList();
            case "ConstructorDeclaration" ->
                    parent.findAll(ConstructorDeclaration.class).stream().filter(n -> identifier == null || n.getNameAsString().equals(identifier)).toList();
            case "BlockStmt" -> findChildOrAll(parent, BlockStmt.class).toList();
            case "ExpressionStmt" -> findChildOrAll(parent, ExpressionStmt.class).toList();
            case "MethodCallExpr" ->
                    findChildOrAll(parent, MethodCallExpr.class).filter(n -> identifier == null || n.getNameAsString().equals(identifier)).toList();
            case "InitializerDeclaration" -> parent.findAll(InitializerDeclaration.class);
            default -> List.of();
        };
    }

    private static void applyInclude(Node targetNode, String content) {
        if (targetNode instanceof BlockStmt block) {
            Statement stmt = StaticJavaParser.parseStatement(content);
            block.addStatement(stmt);
        } else if (targetNode instanceof ClassOrInterfaceDeclaration clazz) {
            clazz.addMember(StaticJavaParser.parseBodyDeclaration(content));
        }
    }

    private static void applyRemove(Node targetNode) {
        targetNode.remove();
    }

    private static void applyReplace(Node targetNode, String content) {
        Node replacement;
        if (targetNode instanceof Statement) {
            replacement = StaticJavaParser.parseStatement(content);
        } else if (targetNode instanceof BodyDeclaration) {
            replacement = StaticJavaParser.parseBodyDeclaration(content);
        } else {
            System.err.println("Unsupported node type for REPLACE: " + targetNode.getClass().getSimpleName());
            return;
        }
        targetNode.replace(replacement);
    }
}
