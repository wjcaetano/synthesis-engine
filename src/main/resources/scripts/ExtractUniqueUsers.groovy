import com.capco.brsp.synthesisengine.service.IExecutor
import org.springframework.context.ApplicationContext

/**
 * ExtractUniqueUsers.groovy
 *
 * Extracts unique users (assignee + reporter) from raw Jira issues.
 * Returns a deduplicated list of user objects.
 */
class ExtractUniqueUsers implements IExecutor {
    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        def rawIssues = projectContext.rawIssues

        if (rawIssues == null || rawIssues.items == null) {
            projectContext.put("rawUsers", [])
            return null
        }

        def users = [] as Set
        def usersList = []

        // Extract assignee and reporter from each issue
        rawIssues.items.each { issue ->
            def fields = issue.fields

            // Add assignee
            if (fields?.assignee != null) {
                def assignee = fields.assignee
                def userKey = assignee.accountId ?: assignee.name ?: assignee.emailAddress

                if (userKey != null && !users.contains(userKey)) {
                    users.add(userKey)
                    usersList.add(assignee)
                }
            }

            // Add reporter
            if (fields?.reporter != null) {
                def reporter = fields.reporter
                def userKey = reporter.accountId ?: reporter.name ?: reporter.emailAddress

                if (userKey != null && !users.contains(userKey)) {
                    users.add(userKey)
                    usersList.add(reporter)
                }
            }
        }

        projectContext.put("rawUsers", usersList)

        // Return null since we already set the context
        return null
    }
}
