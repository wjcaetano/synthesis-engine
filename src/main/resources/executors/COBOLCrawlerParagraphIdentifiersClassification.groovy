import atr.TreeNode
import atr.TreeRewriter
import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap
import com.capco.brsp.synthesisengine.utils.SuperUtils
import com.capco.brsp.synthesisengine.utils.Utils
import io.proleap.cobol.Cobol85Lexer
import org.antlr.v4.runtime.ParserRuleContext
import org.springframework.context.ApplicationContext

import java.util.function.Predicate
import java.util.stream.Collectors

class COBOLCrawlerParagraphIdentifiersClassification implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService.class)
        this.scriptService = applicationContext.getBean(ScriptService.class)

        def dataDivision = projectContext.parent.parent.parent.dataDivision as ConcurrentLinkedHashMap<String, Object>
        def dataDivisionCtx =  dataDivision.meta.ctx as ParserRuleContext
        def paragraphMeta = projectContext.self as ConcurrentLinkedHashMap<String, Object>
        def paragraph = projectContext.self.parent as ConcurrentLinkedHashMap<String, Object>
        def paragraphCtx = paragraphMeta.ctx as ParserRuleContext

        var dataDivisionTree = new TreeRewriter(dataDivisionCtx).rewrite();
        var allDataDivisionIdentifiers = getFilteredNodes(dataDivisionTree, it -> {
            try {
                return it.getLabel().contains("Entry") && it.getChildren() != null && it.getChildren().size() > 1;
            } catch (Exception ex) {
                return false;
            }
        }).stream()
                .map(it -> Utils.nvl(it.getChildren().get(1).getLabel(), "<NULL>"))
                .collect(Collectors.toSet());

        def allContextIdentifiers = getIdentifiersSet(paragraphCtx)
        def group = allContextIdentifiers.stream().collect(Collectors.partitioningBy(allDataDivisionIdentifiers::contains, Collectors.toSet()))

        paragraph.put('dataIdentifiers', Utils.convertToConcurrent(group.getOrDefault(true, Collections.emptySet())))
        paragraph.put('nonDataIdentifiers', Utils.convertToConcurrent(group.getOrDefault(false, Collections.emptySet())))

        return group
    }

    static Set<String> getIdentifiersSet(ParserRuleContext ctx) {
        var paragraphTree = new TreeRewriter(ctx).rewrite();
        return getFilteredNodes(paragraphTree, it2 -> Objects.equals(it2.getTokenType(), Cobol85Lexer.IDENTIFIER)).stream().map(TreeNode::getLabel).collect(Collectors.toSet());
    }

    static List<TreeNode> getFilteredNodes(TreeNode tree, Predicate<TreeNode> clauseToFilter) {
        var nodeFlat = new ArrayList<TreeNode>();
        if (clauseToFilter.test(tree)) {
            nodeFlat.add(tree);
        }

        return getFilteredNodes(tree, clauseToFilter, nodeFlat);
    }

    static List<TreeNode> getFilteredNodes(TreeNode tree, Predicate<TreeNode> clauseToFilter, List<TreeNode> nodeFlat) {
        for (TreeNode child : tree.getChildren()) {
            if (clauseToFilter.test(child)) {
                nodeFlat.add(child);
            }
            getFilteredNodes(child, clauseToFilter, nodeFlat);
        }

        return nodeFlat;
    }
}
