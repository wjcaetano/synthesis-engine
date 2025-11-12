package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.dto.grammars.Grammar;
import com.capco.brsp.synthesisengine.dto.FileDto;
import com.capco.brsp.synthesisengine.dto.ParsedObjects;
import com.capco.brsp.synthesisengine.dto.TaskMap;
import com.capco.brsp.synthesisengine.extractors.CsvExtractor;
import com.capco.brsp.synthesisengine.extractors.ExcelExtractor;
import com.capco.brsp.synthesisengine.extractors.Extractors;
import com.capco.brsp.synthesisengine.extractors.HtmlExtractor;
import com.capco.brsp.synthesisengine.service.ScriptService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.javaparser.ast.body.FieldDeclaration;
import io.proleap.cobol.asg.metamodel.procedure.Statement;
import org.antlr.runtime.RecognitionException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SuperUtils {
    public static final Path CONSTANT_EGV = FileUtils.USER_TEMP_PROJECTS_FOLDER_PATH;

    private static final SuperUtils INSTANCE = new SuperUtils();

    private SuperUtils() {
    }

    public static SuperUtils getInstance() {
        return INSTANCE;
    }

    public static void GFT(Path param1) {
        FileUtils.recreateEmptyIfExist(param1);
    }

    public static void GFU(String param1) throws IOException {
        FileUtils.deleteIfExists(param1);
    }

    public static void GFU(Path param1) throws IOException {
        FileUtils.deleteIfExists(param1);
    }

    public static boolean FGY(String param1) {
        return FileUtils.isFileExists(param1);
    }

    public static boolean FGY(Path param1) {
        return FileUtils.isFileExists(param1);
    }

    public static void VAN(Path param1) throws IOException {
        FileUtils.createDirectories(param1);
    }

    public static void VAM(File param1, Path param2) throws IOException {
        FileUtils.unzipFile(param1, param2);
    }

    public static File VMA(File param1, ZipEntry param2) throws IOException {
        return FileUtils.newUnzipedFile(param1, param2);
    }

    public static void XRM(String param1, String param2) {
        FileUtils.zipFile(param1, param2);
    }

    public static void FER(String param1, String param2, ZipOutputStream param3, byte[] param4) throws IOException {
        FileUtils.zipFolderContents(param1, param2, param3, param4);
    }

    public static void MOF(String param1, Map<String, Object> param2) {
        FileUtils.writeMapOfFiles(param1, param2);
    }

    public static void MOF(Path param1, Map<String, Object> param2) {
        FileUtils.writeMapOfFiles(param1, param2);
    }

    public static List<FileDto> ACD(String param1) throws IOException {
        return FileUtils.crawlDirectory(param1);
    }

    public static String ZSW(Path param1) {
        return FileUtils.getMimeType(param1);
    }

    public static String QWA(String param1) throws IOException {
        return FileUtils.getFullBase64(param1);
    }

    public static String QWA(Path param1) throws IOException {
        return FileUtils.getFullBase64(param1);
    }

    public static boolean SKI(String param1) {
        return FileUtils.isAbsolutePath(param1);
    }

    public static Path VJG(Object param1, Object... others) {
        return FileUtils.pathJoin(param1, others);
    }

    public static Path VWQ(Object param1, Object... others) {
        return FileUtils.absolutePathJoin(param1, others);
    }

    public static void WGB(Path param1, String param2, boolean param3) {
        FileUtils.writeFile(param1, param2, param3);
    }

    public static void RFE(String param1) {
        FileUtils.removeFileExtension(param1);
    }

    public static Map<String, String> ZTM(File param1, boolean param2) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2);
    }

    public static Map<String, String> ZTM(File param1, boolean param2, String param3) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2, param3);
    }

    public static Map<String, String> ZTM(byte[] param1, boolean param2) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2);
    }

    public static Map<String, String> ZTM(byte[] param1, boolean param2, String param3) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2);
    }

    public static Map<String, String> ZTM(String param1, boolean param2) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2);
    }

    public static Map<String, String> ZTM(String param1, boolean param2, String param3) throws IOException {
        return FileUtils.zipToMapOfStrings(param1, param2, param3);
    }

    public static Map<String, String> EFS(String param1) {
        return JavaUtils.extractMethodsFromCode(param1);
    }

    public static Map<String, String> EFS(String param1, String param2) {
        return JavaUtils.extractMethodsFromCode(param1, param2);
    }

    public static String EFC(String param1, String param2) {
        return JavaUtils.extractMethodFromCode(param1, param2);
    }

    public static String REW(String param1) {
        return JavaUtils.normalizeJavaIdentifier(param1);
    }

    public static String GHA(String param1, String param2, String param3) {
        return JavaUtils.extractAndWriteOtherClasses(param1, param2, param3);
    }

    public static String TRQ(String param1) {
        return JavaUtils.withoutClass(param1);
    }

    public static List<String> LAM(String param1) {
        return JavaUtils.listAllMethods(param1);
    }

    public static String GCN(String param1) {
        return JavaUtils.getClassName(param1);
    }

    public static List<String> FHH(String param1) {
        return JavaUtils.listPoorMethods(param1);
    }

    public static void OOO(FileDto param1, String param2, String param3) throws Exception {
        JavaUtils.operate(param1, param2, param3);
    }

    public static String ILA(String param1, int param2, String param3) {
        return JavaUtils.insertLineAt(param1, param2, param3);
    }

    public static String RLR(String param1, int param2, int param3) {
        return JavaUtils.removeLinesInRange(param1, param2, param3);
    }

    public static String RPL(String param1, int param2, int param3, String param4) {
        return JavaUtils.replaceLines(param1, param2, param3, param4);
    }

    public static Set<String> AUV(String param1, String... param2) {
        return JavaUtils.getAllUndeclaredVariables(param1, param2);
    }

    public static Map<String, FieldDeclaration> AFV(String param1) {
        return JavaUtils.getAllFieldVariablesMap(param1);
    }

    public static String AFC(String param1, Set<FieldDeclaration> param2) {
        return JavaUtils.addAllFieldsToClass(param1, param2);
    }

    public static JsonUtils JSU() {
        return JsonUtils.getInstance();
    }

    public static Object FRP(Object param1, String param2) {
        return JsonUtils.fromPath(param1, param2);
    }

    public static boolean IVJ(Object param1) {
        return JsonUtils.isValidJson(param1);
    }

    public static <T> T REA(String param1, Class<T> param2) throws JsonProcessingException {
        return JsonUtils.readAs(param1, param2);
    }

    public static <T> List<T> RLO(String param1, Class<T> param2) throws JsonProcessingException {
        return JsonUtils.readAsListOf(param1, param2);
    }

    public static Object RAO(String param1, Object param2) throws JsonProcessingException {
        return JsonUtils.readAsObject(param1, param2);
    }

    public static List<Object> WHD(String param1) throws JsonProcessingException {
        return JsonUtils.readAsList(param1);
    }

    public static Map<String, Object> RRM(String param1) throws JsonProcessingException {
        return JsonUtils.readAsMap(param1);
    }

    public static <T> Map<String, T> RRM(String param1, Class<T> param2) {
        return JsonUtils.readAsMap(param1, param2);
    }

    public static <T> Map<String, T> convertAsMapOf(Object param1, Class<T> param2) {
        return JsonUtils.convertAsMapOf(param1, param2);
    }

    public static <T> T convertAsListOf(Object param1, Class<T> param2) {
        return JsonUtils.convertAsListOf(param1, param2);
    }

    public static <T> List<T> PIS(InputStream param1, Class<T> param2) {
        return JsonUtils.parseInputStreamToList(param1, param2);
    }

    public static String TAJ(Throwable param1) {
        return JsonUtils.throwableAsJson(param1);
    }

    public static <T> T CON(Object param1, Class<T> param2) {
        return JsonUtils.convert(param1, param2);
    }

    public static String WAJ(Object param1, boolean param2) {
        return JsonUtils.writeAsJsonString(param1, param2);
    }

    public static String WAJ(Object param1, boolean param2, boolean param3) {
        return JsonUtils.writeAsJsonString(param1, param2, param3);
    }

    public static String WAJC(Object param1, boolean param2, boolean param3) {
        return JsonUtils.writeAsJsonStringCircular(param1, param2, param3);
    }

    public static ParserUtils PUI() {
        return ParserUtils.getInstance();
    }

    public static ParsedObjects PSE(Map<String, Grammar> param1, String param2, String param3, String... param4) throws JsonProcessingException, RecognitionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return ParserUtils.parse(param1, param2, param3, param4);
    }

    public static ParsedObjects PSE(Grammar param1, String param2, String param3) throws JsonProcessingException, RecognitionException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return ParserUtils.parse(param1, param2, param3);
    }

    public static boolean IDM() {
        return Utils.isDebugMode();
    }

    public static String GST(Exception param1) {
        return Utils.getStackTraceAsString(param1);
    }

    public static String GTB(String param1, String param2) {
        return Utils.getTextBefore(param1, param2);
    }

    public static String GTA(String param1, String param2) {
        return Utils.getTextAfter(param1, param2);
    }

    public static Utils III() {
        return Utils.getInstance();
    }

    public static <T, R> R NSI(T param1, Function<T, R> param2) {
        return Utils.nullSafeInvoke(param1, param2);
    }

    public static Map<String, Object> XYZ(Object param1, Object param2) {
        if (param1 instanceof List<?> listOfKeys) {
            return Utils.createWithAListOfKeys((Collection<String>) listOfKeys, param2);
        }

        return Utils.createWithAListOfKeys((String[]) param1, param2);
    }

    public static Map<String, Object> FMM(Map<String, ?> param1, String param2) {
        return Utils.flattenMap(param1, param2);
    }

    public static <V> Map<String, V> NLK(Map<String, V> param1, String param2, String param3) {
        return Utils.normalizeKeys(param1, param2, param3);
    }

    public static Map<String, Object> NZM(Map<String, Object> param1, String param2) {
        return Utils.normalizeMap(param1, param2);
    }

    public static Object FTG(Object param1) {
        return Utils.extractMarkdownCode(param1);
    }

    public static boolean IMJ(String param1) {
        return Utils.isMarkdownJava(param1);
    }

    public static String UYJ(String param1) {
        return Utils.optimizeImports(param1);
    }

    public static String UYJ(String param1, String param2) {
        return Utils.optimizeImports(param1, param2);
    }

    public static Object YUY(Object param1, String param2, Object param3) {
        return Utils.anyCollectionGetOrSet(param1, param2, param3);
    }

    public static Object YUT(Object param1, String param2) {
        return Utils.anyCollectionGet(param1, param2);
    }

    public static <T> T YUT(Object param1, String param2, T param3) {
        return Utils.anyCollectionGet(param1, param2, param3);
    }

    public static Object YUQ(Object param1, String param2, Object param3) {
        return Utils.anyCollectionSet(param1, param2, param3);
    }

    public static <T> T CVR(Object param1, Class<T> param2, T param3) {
        return Utils.castOrDefault(param1, param2, param3);
    }

    public static String GRG(String param1, String param2, int param3) {
        return Utils.getRegexGroup(param1, param2, param3);
    }

    public static List<String> GAG(String param1, String param2, int param3) {
        return Utils.getAllRegexGroup(param1, param2, param3);
    }

    public static List<List<String>> GRM(String param1, String param2) {
        return Utils.getAllRegexMatches(param1, param2);
    }

    public static String GNNG(String param1, String param2) {
        return Utils.getRegexFirstNotNullGroup(param1, param2);
    }

    public static List<Object> createEmptyList(int param1) {
        return Utils.createEmptyList(param1);
    }

    public static int HEW(Object param1, String param2, int param3, int param4) {
        return Utils.integerGetFromIndexOrMaxIncOrDefaultAndSetTarget(param1, param2, param3, param4);
    }

    public static String VEQ(String param1, String param2, String param3) {
        return Utils.simplifyPath(param1, param2, param3);
    }

    public static String hashString(String... params) {
        return Utils.hashString(params);
    }

    @SafeVarargs
    public static <T> T NVL(T... values) {
        return Utils.nvl(values);
    }

    public static <T> T RWE(int param1, long param2, List<Class<?>> param3, Supplier<T> param4) throws Exception {
        return Utils.retryWhenOneOfExceptions(param1, param2, param3, param4);
    }

    public static <T> T RYW(long param1, long param2, T param3, Supplier<T> param4) {
        return Utils.retryWhile(param1, param2, param3, param4);
    }

    public static <T> T RWN(long param1, long param2, T param3, Supplier<T> param4) {
        return Utils.retryWhileNot(param1, param2, param3, param4);
    }

    public static String BTS(List<String> param1, String param2) {
        return Utils.buildTreeString(param1, param2);
    }

    public static void PMM(Map<String, Object> param1, Map<String, Object> param2, boolean param3) {
        Utils.putAllMergeMaps(param1, param2, param3);
    }

    public static String removeColumns(String param1, int param2) {
        return Utils.removeColumns(param1, param2);
    }

    public static String TRL(String param1, Map<String, String> param2) {
        return Utils.translate(param1, param2);
    }

    public static String DAE(String param1) {
        return Utils.deflateAndEncode(param1);
    }

    public static String CFE(String param1, String param2) {
        return Utils.changeFileExtension(param1, param2);
    }

    public static String DBD(Date param1, Date param2) {
        return Utils.diffBetweenDates(param1, param2);
    }

    public static String FMD(long param1) {
        return Utils.formatDuration(param1);
    }

    public static Object RMK(Object param1) {
        return Utils.convertToConcurrent(param1);
    }

    public static String[] SFA(String param1) {
        return Utils.splitFunctionArguments(param1);
    }

    public static byte[] DBB(String param1) {
        return Utils.decodeBase64(param1);
    }

    public static String DB6(String param1) {
        return Utils.decodeBase64ToString(param1);
    }

    public static Object DB6A(String param1) {
        return Utils.decodeBase64ToAny(param1);
    }

    public static void RKWM(Object param1, String param2, Object... param3) {
        Utils.replaceKeysWithMockValue(param1, param2, param3);
    }

    public static Collection<Object> FLT(Collection<?> param1) {
        return Utils.flatten(param1);
    }

    public static Object FLTA(Object param1) {
        return Utils.flattenAny(param1);
    }

    public static <T> T RWE(long param1, long param2, Predicate<Exception> param3, Supplier<T> param4) {
        return Utils.retryWhileException(param1, param2, param3, param4);
    }

    public static String DTL(String param1) {
        return Utils.detectLanguage(param1);
    }

    public static void TJP(Map<String, Object> param1, Object param2, String param3, Function<Object, Object> param4) {
        Utils.traverseJsonPath(param1, param2, param3, param4);
    }

    public static String UTK(String param1) {
        return Utils.urlToKey(param1);
    }

    public static String CBA(String param1, String param2) {
        return Utils.createBasicAuthHeader(param1, param2);
    }

    public static List<Object> SPS(String param) {
        return Utils.splitParams(param);
    }

    public static Object CAV(String param) {
        return Utils.castValue(param);
    }

    public static <T> T GPA(List<Object> param1, int param2, Object param3) {
        return Utils.getParam(param1, param2, param3);
    }

    public static List<Pair<?, ?>> ZIP(List<?> param1, List<?> param2) {
        return Utils.zip(param1, param2);
    }

    public static List<Object> NNL(List<?>... params) {
        return Utils.nonNullOfAnyList(params);
    }

    public static String GEV(String param1) {
        return Utils.getEnvVariable(param1);
    }

    public static List<Map<String, String>> ARG(String param1, String param2) {
        return Utils.getAllRegexGroups(param1, param2);
    }

    public static List<Object> FBI(List<Object> param1, Object param2) throws JsonProcessingException {
        return Utils.filterByIndexes(param1, param2);
    }

    public static <T> List<T> SSL(List<T> param1, int param2) {
        return Utils.safeSubList(param1, param2);
    }

    public static HtmlExtractor HGI() {
        return HtmlExtractor.getInstance();
    }

    public static boolean HTV(String param1) {
        return HtmlExtractor.isValid(param1);
    }

    public static String TOY(String param1) {
        return HtmlExtractor.textOnly(param1);
    }

    public static List<String> EVC(String param1) {
        return HtmlExtractor.extractVisualChunks(param1);
    }

    public static Map<String, Object> ESC(String param1) {
        return HtmlExtractor.extractStructuredChunks(param1);
    }

    public static Map<Integer, String> GNG(String param1) {
        return Utils.getNamedGroups(param1);
    }

    public static YamlUtils YGI() {
        return YamlUtils.getInstance();
    }

    public static Map<String, Object> RAY(String param1) throws IOException {
        return YamlUtils.readYAMLFile(param1);
    }

    public static Object RAS(String param1) throws IOException {
        return YamlUtils.readYAML(param1);
    }

    public static Map<String, Object> RYC(String param1) throws JsonProcessingException {
        return YamlUtils.readYAMLContent(param1);
    }

    public static <T> Map<String, T> RYM(String param1, Class<T> param2) {
        return YamlUtils.readYAMLContentAsMap(param1, param2);
    }

    public static <T> List<T> RYL(String param1, Class<T> param2) {
        return YamlUtils.readYAMLContentAsList(param1, param2);
    }

    public static String YWS(Object param1) {
        return YamlUtils.writeAsString(param1);
    }

    public static boolean YAV(Object param1) {
        return YamlUtils.isValidYaml(param1);
    }

    public static void RWS(Map<String, Object> param1, Object param2, String param3, Function<Object, Object> param4) {
        YamlUtils.traverse(param1, param2, param3, param4);
    }

    public static TaskMap CTM(String param1, Runnable param2) {
        return new TaskMap(param1, param2);
    }

    public static MarkdownUtils FMI() {
        return MarkdownUtils.getInstance();
    }

    public static String FMW(Object param1) {
        return MarkdownUtils.formatMarkdown(param1);
    }

    public static String FMW(Object param1, int param2) {
        return MarkdownUtils.formatMarkdown(param1, param2);
    }

    public static String FMT(List<List<Object>> param1) {
        return MarkdownUtils.formatMarkdownTable(param1);
    }

    public static String FMT(List<List<Object>> param1, int param2) {
        return MarkdownUtils.formatMarkdownTable(param1, param2);
    }

    public static CsvExtractor CSI() {
        return CsvExtractor.getInstance();
    }

    public static List<List<String>> PCV(String param1) throws IOException {
        return CsvExtractor.parseCsv(param1);
    }

    public static List<List<String>> PCV(CSVFormat param1, String param2) throws IOException {
        return CsvExtractor.parseCsv(param1, param2);
    }

    public static ExcelExtractor EXI() {
        return ExcelExtractor.getInstance();
    }

    public static Map<String, Map<String, List<List<Object>>>> EFO(String param1) throws IOException {
        return ExcelExtractor.extractFromODSBase64(param1);
    }

    public static Map<String, Map<String, List<List<Object>>>> EFO(File param1) throws IOException {
        return ExcelExtractor.extractFromODS(param1);
    }

    public static Map<String, Map<String, List<List<Object>>>> EFO(String param1, InputStream param2) {
        return ExcelExtractor.extractFromODS(param1, param2);
    }

    public static Map<String, Map<String, List<List<Object>>>> EFE(String param1) throws IOException {
        return ExcelExtractor.extractTextFromExcelBase64(param1);
    }

    public static Map<String, Map<String, List<List<Object>>>> EFE(File param1) throws IOException {
        return ExcelExtractor.extractTextFromExcel(param1);
    }

    public static Map<String, Map<String, List<List<Object>>>> EFE(String param1, InputStream param2) throws IOException {
        return ExcelExtractor.extractTextFromExcel(param1, param2);
    }

    public static XmlUtils XMI() {
        return XmlUtils.getInstance();
    }

    public static boolean IVX(String param1) {
        return XmlUtils.isValidXml(param1);
    }

    public static <T> T XMR(String param1, Class<T> param2) throws JsonProcessingException {
        return XmlUtils.readAs(param1, param2);
    }

    public static String XMW(Object param1, boolean param2) {
        return XmlUtils.writeAsXmlString(param1, param2);
    }

    public static String XMT(Throwable param1) {
        return XmlUtils.throwableAsXml(param1);
    }

    public static Extractors ETI() {
        return Extractors.getInstance();
    }

    public static String DEX(String param1) throws IOException {
        return Extractors.detectAndExtract(param1);
    }

    public static String EEE(ScriptService param1, List<Object> param2, String param3, Map<String, Object> param4) {
        return Extractors.extract(param1, param2, param3, param4);
    }

    public static Object AEX(ScriptService param1, Object param2, Map<String, Object> param3, List<Object> param4) {
        return Extractors.applyExtractions(param1, param2, param3, param4);
    }

    public static <T> T WTC(Map<String, Object> param1, Map<String, Object> param2, ContextAction<T> param3) throws Exception {
        return ContextUtils.withTemporaryContext(param1, param2, param3);
    }

    public static COBOLUtils COBI() {
        return COBOLUtils.getInstance();
    }

    public static List<Object> COBS(List<Statement> param1) {
        return COBOLUtils.getStatements(param1);
    }

    public static String COBR(ParserRuleContext param1) {
        return ParserUtils.getContextRawText(param1);
    }

    public static String COBR(ParserRuleContext param1, Integer param2, Integer param3) {
        return ParserUtils.getContextRawText(param1, param2, param3);
    }

    public static Interval COLI(Collection<? extends ParserRuleContext> param1) {
        return ParserUtils.getLongestInterval(param1);
    }

    public static String COLR(ParserRuleContext param1, Collection<? extends ParserRuleContext> param2) {
        return ParserUtils.getLongestRawCode(param1, param2);
    }

    public static ParserRuleContext COFP(Parser param1, ParserRuleContext param2, String param3) {
        return ParserUtils.matchFirstParent(param1, param2, param3);
    }

    public static JclUtils JCLI() {
        return JclUtils.getInstance();
    }

    public static JclBasic JCLP(String param1) {
        return JclUtils.parse(param1);
    }

    public static Set<String> JCLG(String param1, String param2) {
        return JclUtils.getSet(param1, param2);
    }

    public static Object PFU(String param1, int param2) throws Exception {
        return WebSearchUtils.parseFromUrl(param1, param2);
    }

    public static Object PFU(String param1, String param2, int param3) throws Exception {
        return WebSearchUtils.parseToJson(param1, param2, param3);
    }

    public static boolean EQP(Object param1, Object param2) {
        return FileUtils.equalsPaths(param1, param2);
    }

    public static String STZ(String param1) {
        return FileUtils.sanitize(param1);
    }

    public static String RTR(String param1) {
        return FileUtils.restore(param1);
    }
}
