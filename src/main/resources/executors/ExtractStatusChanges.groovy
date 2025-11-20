package executors

import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

class ExtractStatusChanges implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def issues = projectContext.classifiedIssues ?: []
        def statusChanges = []

        issues.each { issue ->
            def issueKey = issue.issueKey
            def dailyChanges = issue.dailyStatusChanges ?: []

            dailyChanges.each { change ->
                statusChanges.add([
                        issueKey: issueKey,
                        from: change.from ?: '',
                        to: change.to ?: '',
                        date: change.date ?: '',
                        author: change.author ?: '',
                        authorId: change.authorId ?: 'unknown'
                ])
            }
        }

        projectContext.put("statusChanges", statusChanges)
        return statusChanges
    }
}