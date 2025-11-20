package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

class ExtractParentFields implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        return execute(applicationContext, projectContext, new Object[0])
    }
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext, Object... params) {
        def issues = params.length > 0 ? params[0] : projectContext.get('content')

        if (issues instanceof List) {
            issues.each { issue ->
                if (issue instanceof Map) {
                    def parent = issue.parent
                    if (parent instanceof Map) {
                        // Extract parent.key to root level
                        issue.parentKey = parent.key

                        // Extract parent.issueType to root level
                        def fields = parent.fields
                        if (fields instanceof Map && fields.issuetype instanceof Map) {
                            issue.parentIssueType = fields.issuetype.name
                        }
                    }
                }
            }
        }

        return issues
    }
}