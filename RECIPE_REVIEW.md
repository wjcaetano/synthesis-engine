# 🔍 COMPREHENSIVE RECIPE REVIEW: lmt-jira-report2.yaml

**Date:** 2025-11-17
**Reviewer:** Claude
**Severity Levels:** 🔴 CRITICAL | ⚠️ HIGH | 🟡 MEDIUM | 🔵 LOW

---

## 📋 EXECUTIVE SUMMARY

### Critical Issues Found: 7
### High Priority Issues: 5
### Medium Priority Issues: 3
### Recommendations: 15

**Primary Problem:** Epic nodes are not being persisted correctly due to multiple issues in data flow and prompt logic.

---

## 🔴 CRITICAL ISSUES

### 1. **JOLT Defaults Overwriting Real Data**
**Location:** `jolts.joltJiraToNormalized` (line ~320-335)
**Severity:** 🔴 CRITICAL
**Impact:** Real Jira data is being overwritten with hardcoded defaults

```yaml
{
  "operation": "default",
  "spec": {
    "*": {
      "status": "TO DO",        # ❌ OVERWRITES real status!
      "issueType": "Task",      # ❌ OVERWRITES real issueType!
      "priority": "Medium",     # ❌ OVERWRITES real priority!
      "statusCategory": "To Do"
    }
  }
}
```

**Problem:**
- JOLT `default` operation sets values **even when fields exist**
- This causes ALL issues to have status="TO DO", type="Task", priority="Medium"
- Completely corrupts data integrity

**Fix:**
The `default` operation should ONLY set fields that are **missing/null**, NOT overwrite existing values. However, JOLT's `default` operation works correctly (only fills missing fields). The issue is that if the Jira API doesn't return a field, it gets these defaults.

**Recommendation:**
- Remove `"status": "TO DO"` from defaults (status should ALWAYS come from Jira)
- Remove `"issueType": "Task"` from defaults
- Remove `"priority": "Medium"` from defaults
- Keep only truly optional fields in defaults (description, storyPoints, etc.)

---

### 2. **Epic Extraction Logic Incomplete**
**Location:** `templates.processChunk` prompt
**Severity:** 🔴 CRITICAL
**Impact:** Epics are not being extracted from all issues

**Current Prompt:**
```
### 3. Epic Nodes (ONLY if parent.issueType === "Epic")
```

**Problems:**
1. The prompt doesn't show WHAT to extract from `parent` field
2. The example shows `"name": "parent.summary"` as literal string, not instruction
3. No validation that parent data exists in input JSON
4. Relationship type is `BELONGS_TO_EPIC` but may not match Neo4j query

**Data Flow Analysis:**
```
Jira API → JOLT normalize → enrichedIssues → chunks → LLM → Neo4j
           ↓
         parent: {
           key: "LMT-100",
           summary: "Epic name",
           status: "In Progress",
           priority: "High",
           issueType: "Epic"
         }
```

**Fix Needed:**
- Update prompt to explicitly show how to extract from `input.parent.*` fields
- Add validation examples
- Show correct field mapping

---

### 3. **Missing Epic Persistence from Parent Field**
**Location:** Multiple (JOLT, LLM prompt, Neo4j queries)
**Severity:** 🔴 CRITICAL
**Impact:** Parent epics are collected but not persisted as Epic nodes

**Current Flow:**
1. ✅ Jira API returns `parent` field
2. ✅ JOLT normalizes `parent.key`, `parent.summary`, `parent.issueType`
3. ✅ Data sent to LLM in chunks
4. ❌ **LLM may not extract Epic nodes** (prompt unclear)
5. ❌ **Epic nodes may not be created in Neo4j**
6. ❌ **Queries return empty epic list**

**Root Cause:**
The prompt says "ONLY if parent.issueType === 'Epic'" but doesn't show:
- How to check this condition in the JSON
- What happens if parent is null
- How to extract the epic data

**Evidence from projectModel:**
```yaml
reports/epics: "${#allEpics != null && !#allEpics.isEmpty() ? ... : {}}"
```
This suggests `#allEpics` CAN be empty, which shouldn't happen if issues have parents.

---

### 4. **Relationship Type Mismatch**
**Location:** Neo4j queries and LLM prompt
**Severity:** 🔴 CRITICAL

**In Prompt (line ~654):**
```
3. **BELONGS_TO_EPIC**: Issue → Epic
   - Only if parent.issueType === "Epic"
```

**In Neo4j Query (queryAllEpics):**
```cypher
OPTIONAL MATCH (e)<-[:BELONGS_TO_EPIC]-(i:Issue:JiraReport)
```

**In Neo4j Query (queryDailyChanges):**
```cypher
OPTIONAL MATCH (i:Issue:JiraReport)-[:STATUS_CHANGED]->(sc:StatusChange:JiraReport)
```

**Problem:**
Relationship type is `STATUS_CHANGED` in one place but the prompt says `CHANGED_STATUS`. Need consistency.

Wait, looking again:
- Prompt says: `4. **CHANGED_STATUS**: Issue → StatusChange`
- Query says: `[:STATUS_CHANGED]`

These are different! This will cause relationship not to be found.

**Fix:** Make all relationship types consistent.

---

## ⚠️ HIGH PRIORITY ISSUES

### 5. **Prompt Still Has Old Problems (Not Our Fixes)**
**Location:** `templates.processChunk`
**Severity:** ⚠️ HIGH

The recipe provided by the user does NOT have our recent fixes:
- Still has hardcoded examples ("status": "Open", "priority": "Medium")
- Still has misleading default instruction: `priority="Medium"`
- Missing "ABSOLUTE REQUIREMENT: PRESERVE ALL FIELD VALUES" section
- Still has contradictory markdown rule

**This means the user is using an OLD version of the recipe.**

---

### 6. **User Report Filename Mismatch**
**Location:** `projectModel.generateReports.reports/users`
**Severity:** ⚠️ HIGH

**Current:**
```yaml
.![T(java.lang.String).format('user%04d.html', #this)]
```

**Should be:**
```yaml
.![T(java.lang.String).format('user_%04d.html', #this)]
```

**Impact:** Links in index.html point to `user_0000.html` but files are created as `user0000.html`.

---

### 7. **Issue Node Using Wrong ID Field**
**Location:** `templates.processChunk` prompt line ~597
**Severity:** ⚠️ HIGH

**Current Example:**
```json
{
  "id": "issueKey",
  "type": "Issue",
  "properties": {
    "key": "LMT-123",  // ⚠️ CRITICAL: Use EXACT issue key from input. Do NOT use issue ID.
```

**Problem:**
- Says `"id": "issueKey"` (literal string)
- Should say `"id": "<value from input.issueKey>"`

**Impact:** All Issue nodes will have id="issueKey" instead of id="LMT-123", causing duplicate key errors.

---

### 8. **Neo4j Query Hardcoded Status Values**
**Location:** `templates.queryProjectStats`
**Severity:** ⚠️ HIGH

**Current:**
```cypher
count(CASE WHEN i.status = 'Done' THEN 1 END) AS completedIssues,
count(CASE WHEN i.status = 'In Progress' THEN 1 END) AS inProgress,
count(CASE WHEN i.status = 'To Do' THEN 1 END) AS open,
count(CASE WHEN i.status = 'On Hold' THEN 1 END) AS blocked,
```

**Problem:**
- Hardcoded status values may not match actual Jira statuses
- If Jira uses "IN PROGRESS" (uppercase), query won't match
- If Jira uses custom status names, counts will be wrong

**Fix:** Use case-insensitive matching or status categories.

---

### 9. **Missing Relationship: Epic → Parent Epic**
**Location:** Prompt and data model
**Severity:** ⚠️ HIGH

**Current:** Only creates `Issue → Epic` relationships

**Missing:** Epic-to-Epic hierarchy
- Large epics can have parent epics
- No relationship type defined for this

---

## 🟡 MEDIUM PRIORITY ISSUES

### 10. **Chunk Size Reference Before Stored**
**Location:** `templates.processChunk`
**Severity:** 🟡 MEDIUM

**Current:**
```
## INPUT DATA (${chunk.size} issues)
```

**Problem:** Uses `${chunk.size}` but we already fixed this in the other file to use `${currentChunkSize}`.

This recipe doesn't have the fix.

---

### 11. **Unused Options**
**Location:** `config.options`
**Severity:** 🟡 MEDIUM

```yaml
- name: enableLLMEnrichment
  defaultValue: true

- name: enableAdvancedAnalysis
  defaultValue: true
```

**Problem:** These options are defined but never used in the recipe.

---

### 12. **Missing Error Handling**
**Location:** Multiple templates
**Severity:** 🟡 MEDIUM

No error handling for:
- Empty issue lists
- Failed LLM calls
- Neo4j connection errors
- Null parent fields

---

## 🔵 LOW PRIORITY / IMPROVEMENTS

### 13. **Typo in Neo4j RETURN**
**Location:** `templates.persistSingleNode`
**Severity:** 🔵 LOW

```cypher
RETURN n.id AS nodeIdnodeId
```

Should be:
```cypher
RETURN n.id AS nodeId
```

---

### 14. **Inefficient Epic Extraction**
**Location:** `templates.queryAllEpics`
**Severity:** 🔵 LOW

Uses `OPTIONAL MATCH` twice when could use one query.

---

### 15. **Missing Index Recommendations**
**Location:** Neo4j queries
**Severity:** 🔵 LOW

Should recommend creating indexes:
```cypher
CREATE INDEX issue_key IF NOT EXISTS FOR (i:Issue) ON (i.key);
CREATE INDEX user_accountId IF NOT EXISTS FOR (u:User) ON (u.accountId);
CREATE INDEX epic_key IF NOT EXISTS FOR (e:Epic) ON (e.key);
```

---

## 📊 DATA FLOW ANALYSIS

### Current Flow (with problems marked ❌)

```
┌─────────────────────────────────────────────────────────────┐
│ PHASE 1: DATA COLLECTION                                    │
└─────────────────────────────────────────────────────────────┘
Jira API
  ↓
  fields: [parent, status, priority, issuetype, ...]
  ↓
✅ joltJiraToNormalized
  ↓ maps: parent.key, parent.summary, parent.status, parent.issueType
  ↓
❌ JOLT default operation (OVERWRITES with "TO DO", "Task", "Medium")
  ↓
✅ joltEnrichChangelog
  ↓
✅ enrichedIssues stored

Example enrichedIssues[0]:
{
  "issueKey": "LMT-123",
  "status": "TO DO",  ❌ WRONG! Should be actual status
  "issueType": "Task", ❌ WRONG!
  "priority": "Medium", ❌ WRONG!
  "parent": {
    "key": "LMT-100",
    "summary": "Epic Name",
    "status": "In Progress",  ✅ This is preserved
    "issueType": "Epic"  ✅ This is preserved
  }
}

┌─────────────────────────────────────────────────────────────┐
│ PHASE 2-3: CHUNKING & LLM EXTRACTION                        │
└─────────────────────────────────────────────────────────────┘
enrichedIssues
  ↓
✅ Split into chunks (5 issues each)
  ↓
❌ LLM Prompt (unclear epic extraction instructions)
  ↓
❌ LLM may NOT extract Epic nodes correctly
  ↓
❌ LLM may use wrong relationship type (CHANGED_STATUS vs STATUS_CHANGED)
  ↓
⚠️ chunkKnowledgeGraph (may be missing epics)
  ↓
✅ Merge & deduplicate

┌─────────────────────────────────────────────────────────────┐
│ PHASE 4-5: NEO4J PERSISTENCE                                │
└─────────────────────────────────────────────────────────────┘
knowledgeGraph.nodes
  ↓
❌ May have very few or zero Epic nodes
  ↓
✅ Persist to Neo4j
  ↓
❌ Relationships may fail (wrong type name)

┌─────────────────────────────────────────────────────────────┐
│ PHASE 6: ANALYTICS                                          │
└─────────────────────────────────────────────────────────────┘
queryAllEpics
  ↓
❌ Returns empty or incomplete list
  ↓
allEpics = [] or minimal

┌─────────────────────────────────────────────────────────────┐
│ PHASE 7: REPORT GENERATION                                  │
└─────────────────────────────────────────────────────────────┘
generateIndexReport
  ↓
❌ "No epics found" message shown
  ↓
❌ Epic reports not generated (empty directory)
```

---

## 🛠️ RECOMMENDED FIXES

### Priority 1: Fix JOLT Defaults (CRITICAL)

**Current:**
```yaml
{
  "operation": "default",
  "spec": {
    "*": {
      "description": [],
      "storyPoints": "0",
      "status": "TO DO",      # ❌ REMOVE
      "issueType": "Task",    # ❌ REMOVE
      "priority": "Medium",   # ❌ REMOVE
      "statusCategory": "To Do",  # ❌ REMOVE
      "dueDate": null,
      "resolvedDate": null,
      "assignee": {...},
      "reporter": {...},
      "parent": null,
      "epicLinkField": null
    }
  }
}
```

**Fixed:**
```yaml
{
  "operation": "default",
  "spec": {
    "*": {
      "description": [],
      "storyPoints": "0",
      "dueDate": null,
      "resolvedDate": null,
      "assignee": {
        "name": "Unassigned",
        "email": "",
        "accountId": "unassigned"
      },
      "reporter": {
        "name": "Unknown",
        "email": "",
        "accountId": "unknown"
      },
      "parent": null,
      "epicLinkField": null
    }
  }
}
```

---

### Priority 2: Fix Epic Extraction Prompt (CRITICAL)

**Replace the entire Epic node section:**

```markdown
### 3. Epic Nodes (from parent field)

**EXTRACTION RULE:**
- ONLY create Epic node if `input.parent` exists AND `input.parent.issueType === "Epic"`
- Use parent field data, NOT issue data

**Example Input:**
```json
{
  "issueKey": "LMT-123",
  "parent": {
    "key": "LMT-100",
    "summary": "User Authentication Epic",
    "status": "In Progress",
    "priority": "High",
    "issueType": "Epic"
  }
}
```

**Correct Extraction:**
```json
{
  "id": "LMT-100",  // ← Use parent.key as ID
  "type": "Epic",
  "properties": {
    "key": "LMT-100",
    "name": "User Authentication Epic",  // ← From parent.summary
    "status": "In Progress",  // ← From parent.status (EXACT value!)
    "priority": "High",  // ← From parent.priority (EXACT value!)
    "evidence": "Epic: User Authentication Epic (LMT-100)"
  }
}
```

**Wrong Extraction Examples:**
```json
❌ {"id": "parent.key"}  // Wrong: literal string
❌ {"id": "LMT-123"}  // Wrong: using issue key instead of parent key
❌ {"name": "parent.summary"}  // Wrong: literal string
❌ {"status": "Open"}  // Wrong: using normalized value
✅ {"id": "LMT-100", "name": "User Authentication Epic"}  // Correct!
```

**Validation:**
- Number of Epic nodes should be ≤ number of issues with non-null parent field
- Each Epic.id must match a parent.key from input
- Epic.name must match parent.summary (not be "parent.summary" literal)
```

---

### Priority 3: Fix Relationship Type Names (CRITICAL)

**Make consistent:**

| Prompt Says | Neo4j Query Uses | Fix To |
|-------------|------------------|---------|
| CHANGED_STATUS | STATUS_CHANGED | **CHANGED_STATUS** |
| ASSIGNED_TO | ASSIGNED_TO | ✅ OK |
| REPORTED_BY | REPORTED_BY | ✅ OK |
| BELONGS_TO_EPIC | BELONGS_TO_EPIC | ✅ OK |
| PERFORMED_BY | PERFORMED_BY | ✅ OK |

**Change in queryDailyChanges:**
```cypher
# Current:
OPTIONAL MATCH (i:Issue:JiraReport)-[:STATUS_CHANGED]->(sc:StatusChange:JiraReport)

# Fixed:
OPTIONAL MATCH (i:Issue:JiraReport)-[:CHANGED_STATUS]->(sc:StatusChange:JiraReport)
```

---

### Priority 4: Fix Filename Pattern (HIGH)

```yaml
# Current:
reports/users: "...'user%04d.html'..."

# Fixed:
reports/users: "...'user_%04d.html'..."
```

---

### Priority 5: Apply Our Previous LLM Prompt Fixes (HIGH)

The recipe needs ALL the fixes we made earlier:
1. Remove `priority="Medium"` from defaults
2. Replace hardcoded examples with placeholders
3. Add "ABSOLUTE REQUIREMENT: PRESERVE ALL FIELD VALUES" section
4. Fix markdown code block rule

---

### Priority 6: Fix Neo4j Status Matching (HIGH)

**Current:**
```cypher
count(CASE WHEN i.status = 'Done' THEN 1 END) AS completedIssues
```

**Fixed (case-insensitive):**
```cypher
count(CASE WHEN toLower(i.status) = 'done' THEN 1 END) AS completedIssues,
count(CASE WHEN toLower(i.status) = 'in progress' THEN 1 END) AS inProgress,
```

---

## ✅ VALIDATION CHECKLIST

After applying fixes, verify:

- [ ] Run recipe and check Neo4j: `MATCH (e:Epic:JiraReport) RETURN count(e)`
  - Should be > 0 if issues have parent epics
- [ ] Check relationship: `MATCH ()-[r:BELONGS_TO_EPIC]->() RETURN count(r)`
  - Should match number of issues with parents
- [ ] Check status values: `MATCH (i:Issue) RETURN DISTINCT i.status`
  - Should show actual Jira statuses, NOT "TO DO"
- [ ] Check priority values: `MATCH (i:Issue) RETURN DISTINCT i.priority`
  - Should show actual Jira priorities, NOT all "Medium"
- [ ] Verify epic reports generated: `ls reports/epics/`
  - Should have `epic_0000.html`, `epic_0001.html`, etc.
- [ ] Check index.html epic section
  - Should NOT show "No epics found" if epics exist

---

## 📝 SUMMARY

**Total Issues Found:** 15
**Critical Issues:** 4
**Estimated Fix Time:** 2-3 hours
**Risk if Not Fixed:** Data corruption, missing epics, incorrect reports

**Recommended Action Plan:**
1. ✅ Apply JOLT fix (remove bad defaults) - 15 min
2. ✅ Update epic extraction prompt - 30 min
3. ✅ Fix relationship type consistency - 15 min
4. ✅ Apply all previous LLM prompt fixes - 30 min
5. ✅ Fix filename pattern - 5 min
6. ✅ Fix Neo4j queries - 20 min
7. ✅ Test with sample data - 60 min

**Expected Outcome:**
- ✅ All epics extracted and persisted
- ✅ Correct relationships created
- ✅ Reports generated with accurate data
- ✅ No data corruption from defaults

