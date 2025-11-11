import antlr4.AdabasNaturalParser
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.ScriptService2
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.service.SuperService2
import com.capco.brsp.synthesisengine.utils.ParserUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.antlr.v4.runtime.ParserRuleContext
import org.springframework.context.ApplicationContext

class NaturalCrawlerDefineDataStatement implements IExecutor {
    SuperService2 superService = null
    ScriptService2 scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    class DefineDataAndFields {
        DefineDataAndFields(AdabasNaturalParser.DefineDataStatementContext dds, List<AdabasNaturalParser.FieldDefinitionContext> fdcList) {
            this.defineDataStatementContext = dds
            this.fields = fdcList
        }

        AdabasNaturalParser.DefineDataStatementContext defineDataStatementContext;
        List<AdabasNaturalParser.FieldDefinitionContext> fields = []
    }

    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        this.superService = applicationContext.getBean(SuperService2.class)
        this.scriptService = applicationContext.getBean(ScriptService2.class)

        def self = projectContext.self as Map<String, Object>
        def meta = self.meta as Map<String, Object>
        def ctx = meta.ctx as ParserRuleContext

        def naturalStatements = [] as List<AdabasNaturalParser.NaturalStatementContext>
        if (ctx instanceof AdabasNaturalParser.ProgramContext) {
            naturalStatements = ctx.naturalStatement() ?: [] as List<AdabasNaturalParser.NaturalStatementContext>
        } else if (ctx instanceof AdabasNaturalParser.SubroutineStatementContext) {
            naturalStatements = ctx.naturalStatement() ?: [] as List<AdabasNaturalParser.NaturalStatementContext>
        } else {
            println "Unexpected context type: ${ctx.class.name}. Expected ProgramContext or SubroutineStatementContext."
        }

        def fieldsPerArea = naturalStatements
                .collect { it.stmtScope()?.notMainCode()?.defineDataStatement() }
                .findAll { dds -> dds && dds.defineDataOptions()?.any { opt -> opt.dataArea() } }
                .groupBy { dds ->
                    dds.defineDataOptions()
                            .collectMany { opt -> opt.dataArea() ?: [] }
                            .first()
                            .getText()
                            .trim()
                            .toUpperCase()
                }
                .collectEntries { area, ddss ->
                    def fields = ddss.collect { dds ->
                        new DefineDataAndFields(dds, dds.defineDataOptions().collectMany { opt -> opt.fieldDefinition() ?: [] })
                    }

                    if (fields.size() > 1) {
                        throw new IllegalStateException(
                                "Multiple Define Data statements found for area '${area}'. " +
                                        "This executor expects only one Define Data statement per area.")
                    }

                    if (!fields || fields.isEmpty()) {
                        return null
                    }

                    [(area): fields.first()]
                } as Map<String, DefineDataAndFields>

        def dataArea = params.first() as String

        def defineDataAndFields = fieldsPerArea[dataArea]
        if (!defineDataAndFields) {
            return null
        }

        def filePath = (projectContext.filePath ?: "<UNKNOWN>") as String
        def fileNameWithoutExtension = (projectContext.fileNameWithoutExtension ?: "<UNKNOWN>") as String

        def dataAreaKey = "${self.key ?: 'nddm:' + fileNameWithoutExtension}|dataArea:${dataArea}".toString()
        def items = processNaturalDataItems(filePath, [dataAreaKey], defineDataAndFields)

        def dataAreaName = "NaturalDefineData${self.key == null ? 'Module' : dataArea.toLowerCase().capitalize()}".toString()
        return [
                labels: [dataAreaName],
                key: dataAreaKey,
                name: dataAreaName,
                items: items,
                filePath: filePath,
                rawCode: ParserUtils.getContextRawText(defineDataAndFields.getDefineDataStatementContext()),
                firstIndex: defineDataAndFields.getDefineDataStatementContext().getStart().getStartIndex(),
                lastIndex: defineDataAndFields.getDefineDataStatementContext().getStop().getStopIndex(),
                firstLine: defineDataAndFields.getDefineDataStatementContext().getStart().getLine(),
                lastLine: defineDataAndFields.getDefineDataStatementContext().getStop().getLine()
        ]
    }

    static List<Map<String, Object>> processNaturalDataItems(
            String filePath,
            List<String> keyPath,
            DefineDataAndFields defineDataAndFields) {

        def parseLevel = { String s ->
            if (!s) return 1
            def m = (s =~ /\d+/)
            m.find() ? (m.group(0) as int) : 1
        }

        // helper: find an existing sibling variable (same parent, same level) by name
        def findSiblingAtLevel = { List<Map> rootList, Map parentNode, int level, String varName ->
            def siblings = (level == 1) ? rootList : (parentNode?.children ?: [])
            siblings?.find { it.name?.equalsIgnoreCase(varName) && (it.labels ?: []).contains('NaturalVariable') }
        }

        def root = []
        def levelStack = [:] // Map<Integer, Map> — level -> node
        def keyStack = []    // Mirrors the nesting path of keys

        defineDataAndFields.getFields().each { fd ->
            def rawCode   = ParserUtils.getContextRawText(fd)
            def level     = parseLevel(fd?.fieldNumber()?.getText())
            def isRedef   = (fd?.REDEFINE() != null)
            def idTokens  = fd?.fieldIdentifier() ?: []
            def name      = idTokens ? idTokens[0]?.ID()?.getText() as String : null
            def dataType  = fd?.dataType()?.dataTypeSpecifier()?.ID()?[0]?.getText() as String

            while (keyStack.size() >= level) {
                keyStack.pop()
            }

            def parent = levelStack[level - 1]
            def parentKey = ([*keyPath, *keyStack]).join('|')

            if (isRedef) {
                // This is a REDEFINE group node. It reuses storage of "name" (the target).
                // It becomes the parent for subsequent (level+1) fields.
                def redefineKey = parentKey + '|redefOf:' + (name ?: '<UNKNOWN>')
                // try to link back to the target variable (if it already appeared)
                def targetSibling = findSiblingAtLevel(root, parent, level, name ?: '')
                def node = [
                        labels          : ['NaturalRedefine', 'Variable'],
                        filePath        : filePath,
                        rawCode         : rawCode,
                        level           : level,
                        // keep "name" only as the target being redefined; the group itself has no new identifier
                        name            : name,
                        redefineOf      : name,
                        redefineTargetKey: targetSibling?.key,  // null if not found (e.g., REDEFINE before target appears)
                        key             : redefineKey,
                        dataType        : 'REDEFINE',
                        firstIndex      : fd.getStart().getStartIndex(),
                        lastIndex       : fd.getStop().getStopIndex(),
                        firstLine       : fd.getStart().getLine(),
                        lastLine        : fd.getStop().getLine(),
                        children        : []
                ]

                if (level == 1) {
                    root << node
                } else {
                    if (parent) {
                        parent.children << node
                    } else {
                        // malformed structure; best-effort: attach to root
                        root << node
                    }
                }

                levelStack[level] = node
                // push a distinct key segment to avoid clashing with 'nvar:<name>'
                keyStack << 'redefOf:' + (name ?: '<UNKNOWN>')
                return // done for this token
            }

            // Regular variable node (not a REDEFINE clause)
            def fullKey = parentKey + '|nvar:' + (name ?: '<UNKNOWN>')

            def node = [
                    labels    : ['NaturalVariable', 'Variable'],
                    filePath  : filePath,
                    rawCode   : rawCode,
                    level     : level,
                    name      : name,
                    key       : fullKey,
                    dataType  : dataType,
                    firstIndex: fd.getStart().getStartIndex(),
                    lastIndex : fd.getStop().getStopIndex(),
                    firstLine : fd.getStart().getLine(),
                    lastLine  : fd.getStop().getLine(),
                    children  : []
            ]

            if (level == 1) {
                root << node
            } else {
                if (parent) {
                    parent.children << node
                } else {
                    // malformed structure; best-effort: attach to root
                    root << node
                }
            }

            levelStack[level] = node
            keyStack << 'nvar:' + (name ?: '<UNKNOWN>')
        }

        return root
    }
}
