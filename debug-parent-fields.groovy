// Debug script to check parent fields in classifiedIssues
// Run this in Groovy console or as a test

def classifiedIssues = projectContext.classifiedIssues

println "=== DEBUG: Parent Fields in classifiedIssues ==="
println "Total issues: ${classifiedIssues?.size() ?: 0}"
println ""

def issuesWithParent = 0
def issuesWithEpicLink = 0

classifiedIssues?.eachWithIndex { issue, idx ->
    def hasParentKey = issue.parentKey != null && !issue.parentKey.toString().isEmpty()
    def hasParentType = issue.parentIssueType != null && !issue.parentIssueType.toString().isEmpty()
    def hasEpicLink = issue.epicLinkField != null && !issue.epicLinkField.toString().isEmpty()

    if (hasParentKey || hasParentType || hasEpicLink) {
        println "Issue #${idx}: ${issue.issueKey}"
        println "  parentKey: '${issue.parentKey}'"
        println "  parentIssueType: '${issue.parentIssueType}'"
        println "  epicLinkField: '${issue.epicLinkField}'"
        println "  issueType: '${issue.issueType}'"
        println ""

        if (hasParentKey) issuesWithParent++
        if (hasEpicLink) issuesWithEpicLink++
    }
}

println "Summary:"
println "  Issues with parentKey: ${issuesWithParent}"
println "  Issues with epicLinkField: ${issuesWithEpicLink}"
println ""

// Check if Epic nodes exist
def normalizedEpics = projectContext.normalizedEpics
println "Total Epics in normalizedEpics: ${normalizedEpics?.size() ?: 0}"
normalizedEpics?.each { epic ->
    println "  Epic: ${epic.epicKey} - ${epic.epicName}"
}
