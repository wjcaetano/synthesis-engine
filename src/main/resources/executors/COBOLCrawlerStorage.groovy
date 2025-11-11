import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedList
import com.capco.brsp.synthesisengine.utils.ParserUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import io.proleap.cobol.asg.metamodel.FigurativeConstant
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntry
import io.proleap.cobol.asg.metamodel.data.datadescription.impl.DataDescriptionEntryGroupImpl
import org.springframework.context.ApplicationContext

class COBOLCrawlerStorage implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService)
        this.scriptService = applicationContext.getBean(ScriptService)

        def parent = projectContext.parent as Map<String, Object>
        def self = projectContext.self as Map<String, Object>
        def childLabels = self.meta.childLabels as List<String>
        def dataEntryLevel1 = projectContext.item as DataDescriptionEntry
        def filePath = (projectContext.filePath ?: "unknown") as String

        return processCOBOLDataEntries(parent, 0, childLabels, dataEntryLevel1, filePath, null)
    }

    Map<String, Object> processCOBOLDataEntries(Map<String, Object> parent, int index, List<String> childLabels, DataDescriptionEntry entry, String filePath, String parentName) {
        def rawCode = ParserUtils.getContextRawText(entry.ctx)
        def name = entry.name ?: 'FILLER'
        def item = Utils.convertToConcurrent([
                key       : "${parent.key}|i:${index};s:${name}".toString(),
                labels    : childLabels,
                filePath  : filePath,
                name      : name,
                parentName: parentName,
                level     : entry.levelNumber,
                mvcTarget : "DATA",
                rawCode   : rawCode
        ]) as Map<String, Object>

        if (entry instanceof DataDescriptionEntryGroupImpl) {
            def pic = entry.pictureClause?.pictureString
            if (pic) {
                def type = pic.replaceAll(/[S,]/, '').find(/^[X9APZNEGUB]+/)
                def scale = (pic =~ /\((\d+)\)/)*.getAt(1)?.getAt(1)?.toInteger()
                item.precision = scale ?: type?.size() ?: 0
                item.type = pic.contains('V') || pic.any { it in ['P','Z','E'] } ? 'decimal' :
                        type?.contains('9') ? 'integer' :
                                type?.any { it in ['X','A','B','N','G','U'] } ? 'text' : 'unknown'

                if (pic.contains('V')) {
                    def afterV = pic.split('V')?.getAt(1)
                    def vScale = (afterV =~ /\((\d+)\)/)*.getAt(1)?.getAt(1)?.toInteger() ?: afterV?.size() ?: 0
                    item.scale = vScale
                }
            }

            def valClause = entry.valueClause
            if (valClause) {
                item.valuesClause = ParserUtils.getContextRawText(valClause.ctx)
                if (entry.levelNumber != 88) {
                    def val = valClause.valueIntervals?.first()?.fromValueStmt
                    def rawVal = ParserUtils.getContextRawText(val?.ctx)?.trim()
                    def actualVal = val?.value
                    item.values = (actualVal instanceof FigurativeConstant) ? actualVal.figurativeConstantType?.name() :
                            rawVal?.matches(/^X['"]\d+['"]$/) ? rawVal :
                                    actualVal?.toString()
                }
            }

            item.children = new ConcurrentLinkedList<>()
            def innerDataDescriptionEntries = entry.dataDescriptionEntries ?: []
            innerDataDescriptionEntries.eachWithIndex { DataDescriptionEntry inner, int i ->
                item.children.add(processCOBOLDataEntries(item, i, childLabels, inner, filePath, name))
            }

            item.fullRawCode = ([rawCode] + (item.children?.collect { it.rawCode } ?: [])).join('\n')
        } else {
            item.fullRawCode = rawCode
        }

        item.firstIndex = entry.ctx.getStart().getStartIndex()
        item.lastIndex = entry.ctx.getStop().getStopIndex()
        item.firstLine = entry.ctx.getStart().getLine()
        item.lastLine = entry.ctx.getStop().getLine()

        return item
    }
}
