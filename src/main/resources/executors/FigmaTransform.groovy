import com.capco.brsp.synthesisengine.service.IExecutor
import com.capco.brsp.synthesisengine.service.ScriptService
import com.capco.brsp.synthesisengine.service.SuperService
import com.capco.brsp.synthesisengine.utils.JsonUtils
import com.capco.brsp.synthesisengine.utils.SuperUtils
import org.springframework.context.ApplicationContext

class FigmaTransform implements IExecutor {
    SuperService superService = null
    ScriptService scriptService = null
    SuperUtils superUtils = SuperUtils.getInstance()

    @Override
    Object execute(ApplicationContext applicationContext, Map<String, Object> projectContext) {
        this.superService = applicationContext.getBean(SuperService)
        this.scriptService = applicationContext.getBean(ScriptService)

        def figmaContent = projectContext.figmaContent as Map<String, Object>
        def api = projectContext.get('$api') as Map<String, Object>
        def configs = api?.configs as Map<String, Object>
        def options = configs?.options as Map<String, Object>

        def nodesList = collectNodesListAndGrouped(figmaContent)
        def htmls = generateMultipleHtmls(figmaContent, [
                figmaToken  : options?.figmaApiToken,
                figmaFileKey: options?.fileKey
        ])

        // Enrich the nodesList: for each type and each item, attach html content when available
        def htmlMap = (htmls?.htmlByName ?: [:]) as Map
        def enriched = [] as List
        nodesList.each { typeEntry ->
            typeEntry.each { typeName, items ->
                def newItems = (items ?: []).collect { item ->
                    def raw = (item.cleanName ?: item.name ?: '') as String
                    def baseKey = sanitize(raw.replaceAll('[^a-zA-Z0-9]', '')) + ".html"
                    def content = htmlMap.containsKey(baseKey) ? htmlMap[baseKey] : null
                    def copy = [:] + item
                    copy.htmlKey = baseKey
                    copy.html = content
                    copy
                }
                enriched << [(typeName): newItems]
            }
        }

        projectContext.put("figmaTransformed", enriched)
        return enriched
    }

    // Preserve geometry and key fields so the generator can work from processed structure if needed
    private Map shallowNodeForStructure(Map node, int depth) {
        if (node == null) return [:]
        def m = [
                id   : node.id,
                type : node.type,
                name : node.name,
                depth: depth,
                // geometry commonly used by generator
                absoluteBoundingBox: node.absoluteBoundingBox,
                // text + styling
                characters         : node.characters,
                style              : node.style,
                fills              : node.fills,
                strokes            : node.strokes,
                strokeWeight       : node.strokeWeight,
                cornerRadius       : node.cornerRadius,
                effects            : node.effects,
                componentId        : node.componentId
        ]
        def kids = (node.children ?: []) as List
        if (kids) {
            m.children = kids.collect { shallowNodeForStructure((Map) it, depth + 1) }
        }
        return m
    }

    def processFigmaPayload(Map<String, Object> data) {

        def components = data.components ?: [:]
        def styles = data.styles ?: [:]
        def document = data.document ?: [:]

        def pendingComponentCalls = [] as Set

        def traverse
        traverse = { node, depth = 0 ->

            def info = [
                    id   : node.id,
                    type : node.type,
                    name : node.name,
                    depth: depth
            ]

            if (node.type == 'INSTANCE' && node.componentId) {
                info.componentId = node.componentId
                def comp = components[node.componentId]
                info.componentName = comp?.name ?: '(Desconhecido)'

                if (!comp) {
                    pendingComponentCalls << node.componentId
                }
            }

            if (node.styles) {
                info.styles = node.styles.collectEntries { k, v ->
                    [(k): styles[v]?.name ?: v]
                }
            }

            if (node.children) {
                info.children = node.children.collect { traverse(it, depth + 1) }
            }

            return info
        }

        def result = traverse(document)
        // Also build a geometry-preserving structure for downstream consumers that expect `structure`
        def structureWithGeometry = shallowNodeForStructure(document as Map, 0)

        return [
                structure : structureWithGeometry,
                components: components.keySet(),
                missing   : pendingComponentCalls as List
        ]
    }


    String rgba(Map color, BigDecimal opacity = 1.0) {
        if (!color) return "transparent"
        def r = Math.round((color.r ?: 0) * 255)
        def g = Math.round((color.g ?: 0) * 255)
        def b = Math.round((color.b ?: 0) * 255)
        def a = opacity != null ? opacity : 1.0
        "rgba(${r},${g},${b},${a})"
    }

    String px(def num, BigDecimal scale = 1) {
        if (num == null) return "0px"
        def v = (num as BigDecimal) * scale
        "${v.setScale(2, java.math.RoundingMode.HALF_UP)}px"
    }

    String esc(Object s) {
        if (s == null) return ""
        String str = s.toString()
        str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    String sanitize(String s) {
        if (!s) return "unnamed"
        def cleaned = s.replaceAll(/[^A-Za-z0-9_\- ]+/, "").trim()
        cleaned.replaceAll(/\s+/, "_")
    }

/** Accept raw (document) or processed (structure). */
    Map normalizeDocument(Map data) {
        if (data.document instanceof Map) return (Map) data.document
        if (data.structure instanceof Map) return (Map) data.structure
        return [:]
    }
/** Accept components map or a collection of IDs (then we return empty map). */
    Map normalizeComponents(def componentsRaw) {
        if (componentsRaw instanceof Map) return (Map) componentsRaw
        return [:]
    }

/** Basic style mapping (solid fills, stroke, corner, text). */
    Map<String, String> styleFromNode(Map node) {
        Map<String, String> style = [:] as Map<String, String>
        def fills = (node.fills ?: []).findAll { it.type == 'SOLID' && (it.visible != false) }
        if (fills) {
            def top = fills.first()
            style.background = rgba(top.color as Map, (top.opacity ?: 1.0) as BigDecimal)
        }
        def strokes = (node.strokes ?: []).findAll { it.type == 'SOLID' && (it.visible != false) }
        if (strokes && (node.strokeWeight ?: 0) > 0) {
            def s = strokes.first()
            style.border = "${(node.strokeWeight ?: 1)}px solid ${rgba(s.color as Map, (s.opacity ?: 1.0) as BigDecimal)}"
        }
        if (node.cornerRadius != null && node.cornerRadius != "MIXED") {
            style.borderRadius = "${node.cornerRadius}px"
        } else {
            // Use local variables to avoid inserting non-style keys in the style map
            def tl = node.topLeftRadius
            def tr = node.topRightRadius
            def br = node.bottomRightRadius
            def bl = node.bottomLeftRadius
            if (tl != null || tr != null || br != null || bl != null) {
                def sTL = (tl != null ? "${tl}px" : "0")
                def sTR = (tr != null ? "${tr}px" : "0")
                def sBR = (br != null ? "${br}px" : "0")
                def sBL = (bl != null ? "${bl}px" : "0")
                style.borderRadius = "${sTL} ${sTR} ${sBR} ${sBL}"
            }
        }
        def shadow = (node.effects ?: []).find { it.type == 'DROP_SHADOW' && (it.visible != false) }
        if (shadow) {
            def c = rgba(shadow.color as Map)
            def ox = (shadow.offset?.x ?: 0)
            def oy = (shadow.offset?.y ?: 0)
            def blur = (shadow.radius ?: 0)
            style.boxShadow = "${ox}px ${oy}px ${blur}px ${c}"
        }
        if (node.type == 'TEXT') {
            def st = node.style ?: [:]
            if (st.fontSize != null && st.fontSize != "MIXED") style.fontSize = "${st.fontSize}px"
            if (st.fontName?.family) style.fontFamily = "'${st.fontName.family}', sans-serif"
            if (st.fontWeight != null && st.fontWeight != "MIXED") style.fontWeight = "${st.fontWeight}"
            if (st.letterSpacing != null && st.letterSpacing != "MIXED") style.letterSpacing = "${st.letterSpacing}px"
            if (st.lineHeightPx != null && st.lineHeightPx != "MIXED") style.lineHeight = "${st.lineHeightPx}px"
            if (fills) style.color = rgba((fills.first().color) as Map, (fills.first().opacity ?: 1.0) as BigDecimal)
            if (st.textAlignHorizontal) style.textAlign = (st.textAlignHorizontal as String).toLowerCase()
        }
        style
    }

/**
 * Compute a node's bbox (absolute space) with fallback:
 * 1) If node.absoluteBoundingBox exists and has positive width/height, use it.
 * 2) Else, compute union of children's bboxes; if none available, fallback to zero box at (x|0,y|0).
 * Returns a map: [x,y,width,height,minX,minY,maxX,maxY].
 */
    Map computeBBox(Map node) {
        def bb = node.absoluteBoundingBox
        if (bb && (bb.width ?: 0) > 0 && (bb.height ?: 0) > 0) {
            return [x   : bb.x ?: 0, y: bb.y ?: 0, width: bb.width ?: 0, height: bb.height ?: 0,
                    minX: bb.x ?: 0, minY: bb.y ?: 0, maxX: (bb.x ?: 0) + (bb.width ?: 0), maxY: (bb.y ?: 0) + (bb.height ?: 0)]
        }
        // Union of children bboxes
        def children = (node.children ?: []) as List<Map>
        def boxes = children.collect { computeBBox(it) }.findAll { (it.width ?: 0) > 0 || (it.height ?: 0) > 0 }
        if (boxes) {
            def minX = boxes.collect { it.minX as BigDecimal }.min() ?: 0
            def minY = boxes.collect { it.minY as BigDecimal }.min() ?: 0
            def maxX = boxes.collect { it.maxX as BigDecimal }.max() ?: 0
            def maxY = boxes.collect { it.maxY as BigDecimal }.max() ?: 0
            return [x: minX, y: minY, width: (maxX - minX), height: (maxY - minY), minX: minX, minY: minY, maxX: maxX, maxY: maxY]
        }
        // Leaf without bbox: fallback to zero-sized box at (x|0,y|0)
        def x = node.x ?: 0
        def y = node.y ?: 0
        [x: x, y: y, width: 0, height: 0, minX: x, minY: y, maxX: x, maxY: y]
    }

/** Build an index of all nodes by id to resolve component nodes inside the same file. */
    Map<String, Map> indexNodesById(Map node) {
        def idx = [:] as Map<String, Map>
        def visit
        visit = { Map n ->
            if (n?.id) idx[n.id as String] = n
            (n.children ?: []).each { visit((Map) it) }
        }
        visit(node)
        idx
    }

    /** Build an index of nodes by both id and key. */
    Map<String, Map> indexNodesByIdAndKey(Map root) {
        def idx = [:] as Map<String, Map>
        def visit
        visit = { Map n ->
            if (n?.id) idx[n.id as String] = n
            def k = n?.key ?: n?.componentKey ?: n?.component_key
            if (k) idx[(k as String)] = n
            (n.children ?: []).each { visit((Map) it) }
        }
        if (root) visit(root)
        idx
    }

    /** Resolve component nodes from component keys, batching node fetches per file_key. */
    Map<String, Map> fetchComponentNodesByKeys(Collection<String> keys, String token) {
        def outByKey = [:] as Map<String, Map>
        if (!keys || !token) return outByKey
        try {
            // 1) Resolve key -> (file_key, node_id)
            def mappings = []
            keys.toSet().each { String key ->
                try {
                    def url = new URL("https://api.figma.com/v1/components/${URLEncoder.encode(key, 'UTF-8')}")
                    def conn = (HttpURLConnection) url.openConnection()
                    conn.setRequestProperty('X-Figma-Token', token)
                    conn.setRequestProperty('Accept', 'application/json')
                    conn.setConnectTimeout(15000)
                    conn.setReadTimeout(30000)
                    def code = conn.responseCode
                    if (code == 200) {
                        def text = conn.inputStream.text
                        Map parsed = JsonUtils.readAsMap(text)
                        def fileKey = parsed?.meta?.file_key
                        def nodeId = parsed?.meta?.node_id
                        if (fileKey && nodeId) {
                            mappings << [key: key, fileKey: fileKey as String, nodeId: nodeId as String]
                        }
                    } else {
                        def err = conn.errorStream?.text
                        System.err.println("Figma component key lookup failed [${code}] for key=${key}: ${err}")
                    }
                } catch (Throwable t) {
                    System.err.println("Figma component key lookup error for key=${key}: ${t.message}")
                }
            }
            if (mappings.isEmpty()) return outByKey

            // 2) Group by file_key and batch fetch nodes
            def byFile = mappings.groupBy { it.fileKey }
            byFile.each { String fileKey, List group ->
                int size = 100
                for (int i = 0; i < group.size(); i += size) {
                    def sub = group.subList(i, Math.min(i + size, group.size()))
                    def idsCsv = sub.collect { URLEncoder.encode(it.nodeId as String, 'UTF-8') }.join(',')
                    try {
                        def url = new URL("https://api.figma.com/v1/files/${fileKey}/nodes?ids=${idsCsv}")
                        def conn = (HttpURLConnection) url.openConnection()
                        conn.setRequestProperty('X-Figma-Token', token)
                        conn.setRequestProperty('Accept', 'application/json')
                        conn.setConnectTimeout(15000)
                        conn.setReadTimeout(30000)
                        def code = conn.responseCode
                        if (code == 200) {
                            def text = conn.inputStream.text
                            Map parsed = JsonUtils.readAsMap(text)
                            Map nodes = (parsed.nodes ?: [:]) as Map
                            sub.each { map ->
                                def nodeEntry = nodes[map.nodeId]
                                if (nodeEntry?.document instanceof Map) {
                                    outByKey[map.key as String] = (Map) nodeEntry.document
                                }
                            }
                        } else {
                            def err = conn.errorStream?.text
                            System.err.println("Figma nodes fetch by file_key failed [${code}] for file=${fileKey}: ${err}")
                        }
                    } catch (Throwable t) {
                        System.err.println("Figma nodes fetch by file_key error for file=${fileKey}: ${t.message}")
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("Figma components by keys fetch error: ${t.message}")
        }
        return outByKey
    }

    /** Render node with separate scaleX/scaleY and origin for coordinate normalization. */
    String renderNode(Map node, Map<String, Map> resolvedComponents, BigDecimal scaleX, BigDecimal scaleY, BigDecimal originX, BigDecimal originY, int depth = 0, Set<String> guard = new HashSet<>()) {
        def bbox = computeBBox(node)
        def left = px(((bbox.x ?: 0) as BigDecimal) - originX, scaleX)
        def top = px(((bbox.y ?: 0) as BigDecimal) - originY, scaleY)
        def w = px(bbox.width ?: 0, scaleX)
        def h = px(bbox.height ?: 0, scaleY)

        Map<String, String> baseStyle = [
                position: "absolute",
                left    : left, top: top,
                width   : w, height: h,
                overflow: "hidden",
                transformOrigin: "0 0"
        ] as Map<String, String>
        baseStyle.putAll(styleFromNode(node))

        // Accumulate inner HTML
        def inner = new StringBuilder()
        boolean handledInstanceChildren = false

        // Optional transform rotation
        if (node.rotation != null && node.rotation != "MIXED") {
            def rotateStr = "rotate(${node.rotation}deg)"
            baseStyle.transform = baseStyle.transform ? baseStyle.transform + " " + rotateStr : rotateStr
        }

        if (node.type == 'TEXT') {
            inner << esc(node.characters)
        } else if (node.type == 'INSTANCE') {
            def compId = (node.componentId ?: node.mainComponentId ?: node.component_id) as String
            def compKey = (node.componentKey ?: node.component_key ?: node.mainComponentKey) as String
            def compNode = compId ? resolvedComponents[compId] : null
            if (!compNode && compKey) compNode = resolvedComponents[compKey]

            if ((compId || compKey) && !guard.contains(compId ?: compKey)) {
                if ((node.children ?: []).size() > 0) {
                    // Prefer instance's own children to preserve overrides; no extra scaling on container
                    def newGuard = new HashSet<>(guard)
                    newGuard.add((compId ?: compKey) as String)
                    (node.children ?: []).each { c ->
                        inner << renderNode((Map) c, resolvedComponents, scaleX, scaleY, (bbox.minX ?: 0) as BigDecimal, (bbox.minY ?: 0) as BigDecimal, depth + 1, newGuard)
                    }
                    handledInstanceChildren = true
                } else if (compNode) {
                    // Use component definition children with container scaling
                    def instBBox = bbox
                    def cmpBBox = computeBBox(compNode)
                    BigDecimal sx = (cmpBBox.width ?: 0) ? ((instBBox.width ?: 0) as BigDecimal) / (cmpBBox.width as BigDecimal) : 1.0
                    BigDecimal sy = (cmpBBox.height ?: 0) ? ((instBBox.height ?: 0) as BigDecimal) / (cmpBBox.height as BigDecimal) : 1.0
                    baseStyle.transform = (baseStyle.transform ? baseStyle.transform + " " : "") + "scale(${sx}, ${sy})"

                    def newGuard = new HashSet<>(guard)
                    newGuard.add((compId ?: compKey) as String)
                    (compNode.children ?: []).each { c ->
                        inner << renderNode((Map) c, resolvedComponents, 1.0, 1.0, (cmpBBox.minX ?: 0) as BigDecimal, (cmpBBox.minY ?: 0) as BigDecimal, depth + 1, newGuard)
                    }
                    handledInstanceChildren = true
                }
            }
            if (!handledInstanceChildren) {
                def label = compId ?: compKey ?: (node.componentName ?: 'Instance')
                inner << "<div style='position:absolute;left:4px;top:4px;font:12px/1.2 ui-monospace,monospace;color:#fff;background:rgba(0,0,0,.4);padding:2px 6px;border-radius:6px;'>${esc(label)}</div>"
            }
        }

        // Render normal children only if not handled by INSTANCE component rendering
        if (!handledInstanceChildren) {
            (node.children ?: []).each { child ->
                inner << renderNode((Map) child, resolvedComponents, scaleX, scaleY, (bbox.minX ?: 0) as BigDecimal, (bbox.minY ?: 0) as BigDecimal, depth + 1, guard)
            }
        }

        def styleStr = baseStyle.collect { k, v -> v ? "${k}:${v}" : null }.findAll { it }.join(";")
        def title = esc("${node.type ?: 'NODE'}${node.name ? " — ${node.name}" : ""}")
        return """
<div class=\"node node-${(node.type ?: 'unknown').toLowerCase()}\" data-id=\"${node.id}\" data-type=\"${node.type}\" title=\"${title}\" style=\"${styleStr}\">${inner}</div>"""
    }

/** CSS wrapper with canvas sized to the root bbox union. */
    String wrapHtml(Map rootNode, Map rootBBox, String innerContent, BigDecimal scale) {
        def totalW = px(rootBBox.width ?: 1440, scale)
        def totalH = px(rootBBox.height ?: 1024, scale)
        def css = """
  body{margin:0;background:#0f0f0f;font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Helvetica,Arial;color:#fff}
  .figma-canvas{position:relative;margin:16px auto;background:#111;outline:1px solid #222;border-radius:12px;box-shadow:0 30px 80px rgba(0,0,0,.45);overflow:auto}
  .node{box-sizing:border-box}
  .node-text{white-space:pre-wrap}
  .node-instance{outline:1px dashed rgba(255,255,255,.2)}
  """.stripIndent()
        return """
<!doctype html>
<html lang=\"en\">
<head>
  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n  <title>${esc(rootNode.name ?: "Frame")}</title>\n  <style>${css}</style>\n</head>\n<body>\n  <main class=\"figma-canvas\" style=\"width:${totalW};height:${totalH};\">\n    ${innerContent}\n  </main>\n</body>\n</html>\n""".trim()
    }

    /** Pick roots (Pages and top-level Frames) from either raw or processed doc. */
    List<Map> collectRoots(Map document) {
        def pages = (document.children ?: []).findAll { it.type == 'CANVAS' }
        if (!pages) return [[type: 'DOCUMENT', node: document, name: document.name ?: 'Document']]

        def roots = []
        pages.each { pageObj ->
            Map page = (Map) pageObj
            roots << [type: 'PAGE', node: page, name: page.name ?: 'Page']
            (page.children ?: []).each { childObj ->
                Map child = (Map) childObj
                if ((child.type as String) in ['FRAME', 'GROUP', 'COMPONENT', 'INSTANCE']) {
                    roots << [type: 'FRAME', node: child, page: page, name: "${page.name ?: 'Page'}__${child.name ?: child.id}"]
                }
            }
        }
        roots
    }

    /**
     * Collect first-level nodes only (direct children of each CANVAS page).
     * Returns a List of single-key maps: [{ 'FRAME': [ {id,name,cleanName,pageName}, ... ] }, { 'COMPONENT': [...] }, ...]
     */
    List<Map> collectTopLevelNodesAsList(Map data) {
        Map document = normalizeDocument(data)
        def byType = [:]

        def pages = (document.children ?: []).findAll { (it?.type ?: '') == 'CANVAS' }
        if (!pages) {
            // No pages: use direct children of document
            (document.children ?: []).each { child ->
                def t = (child?.type ?: 'UNKNOWN') as String
                if (t in ['FRAME', 'GROUP', 'COMPONENT', 'INSTANCE', 'TEXT']) {
                    if (!byType.containsKey(t)) byType[t] = []
                    def kids = (child.children ?: []) as List
                    def kidsSummary = kids.collect { k ->
                        [id: k.id, type: k.type, name: (k.name ?: ''), componentId: (k.componentId ?: k.mainComponentId ?: k.component_id), componentKey: (k.componentKey ?: k.mainComponentKey ?: k.component_key)]
                    }
                    byType[t] << [id: child.id, name: (child.name ?: '') as String, cleanName: cleanDisplayName((child.name ?: '') as String), pageName: (document.name ?: ''), children: kidsSummary]
                }
            }
        } else {
            pages.each { page ->
                (page.children ?: []).each { child ->
                    def t = (child?.type ?: 'UNKNOWN') as String
                    if (t in ['FRAME', 'GROUP', 'COMPONENT', 'INSTANCE', 'TEXT']) {
                        if (!byType.containsKey(t)) byType[t] = []
                        def kids = (child.children ?: []) as List
                        def kidsSummary = kids.collect { k ->
                            [id: k.id, type: k.type, name: (k.name ?: ''), componentId: (k.componentId ?: k.mainComponentId ?: k.component_id), componentKey: (k.componentKey ?: k.mainComponentKey ?: k.component_key)]
                        }
                        byType[t] << [id: child.id, name: (child.name ?: '') as String, cleanName: cleanDisplayName((child.name ?: '') as String), pageName: (document.name ?: ''), children: kidsSummary]
                    }
                }
            }
        }

        def out = [] as List
        // Preserve insertion order of types as encountered
        byType.each { k, v -> out << [(k): v] }
        out
    }

    /** Helper: strip page prefixes like "Page_1__" and return a cleaned display name. */
    String cleanDisplayName(Object rawName) {
        if (!rawName) return 'unnamed'
        String n = rawName.toString()

        // 1) If the name contains a double-underscore separator, prefer the segment after the last '__'
        if (n.contains('__')) {
            n = n.split(/__/)?.last() ?: n
        }

        // 2) Remove common "Page" prefixes such as:
        //    "Page_1__", "Page 1 - ", "Page-1__", "Page_01 -", case-insensitive
        //    Matches: ^\s*page[_\s-]*\d+[\s-_]*(:|--|—|–|-|__)?\s*
        try {
            n = n.replaceFirst(/(?i)^\s*page[_\s-]*\d+[\s-_]*(?:__|[:\-–—]+)?\s*/, '')
        } catch (Throwable e) {
            // If the regex fails for any reason, fall back to the original string (safe)
        }

        // 3) Trim whitespace and return a safe default if empty
        n = (n ?: '').trim()
        return n ? n : 'unnamed'
    }

    /** Wrapper: return only the final list (first-level only) as requested by the user. */
    List<Map> collectNodesListAndGrouped(Map data) {
        collectTopLevelNodesAsList(data)
    }

/** Fetch component nodes by ids if token/fileKey are provided. */
    Map<String, Map> fetchComponentNodesIfPossible(Collection<String> ids, String fileKey, String token) {
        def out = [:] as Map<String, Map>
        if (!ids || !fileKey || !token) return out
        try {
            def chunk = 0
            def idList = ids as List<String>
            int size = 100 // safe chunk size
            while (chunk * size < idList.size()) {
                def sub = idList.subList(chunk * size, Math.min((chunk + 1) * size, idList.size()))
                def idsCsv = sub.collect { URLEncoder.encode(it, 'UTF-8') }.join(',')
                def url = new URL("https://api.figma.com/v1/files/${fileKey}/nodes?ids=${idsCsv}")
                def conn = (HttpURLConnection) url.openConnection()
                conn.setRequestProperty('X-Figma-Token', token)
                conn.setRequestProperty('Accept', 'application/json')
                conn.setConnectTimeout(15000)
                conn.setReadTimeout(30000)
                def code = conn.responseCode
                if (code == 200) {
                    def text = conn.inputStream.text
                    def parsed = JsonUtils.readAsMap(text) as Map
                    Map nodes = (parsed.nodes ?: [:]) as Map
                    nodes.each { k, v ->
                        if (v?.document instanceof Map) out[k as String] = (Map) v.document
                    }
                } else {
                    def err = conn.errorStream?.text
                    System.err.println("Figma nodes fetch failed [${code}]: ${err}")
                }
                chunk++
            }
        } catch (Throwable t) {
            System.err.println("Figma nodes fetch error: ${t.message}")
        }
        return out
    }

/**
 * Main: accepts the map produced by processFigmaPayload OR the raw file map.
 * Returns htmlByName as a map: [ "Screen.html": "<!doctype...", ... ]
 * The file name uses only the frame/screen name (drops prefixes like "Page_1__").
 */
    Map generateMultipleHtmls(Map<String, Object> data, Map opts = [:]) {
        BigDecimal uniformScale = (opts.scale ?: 1.0) as BigDecimal

        Map document = normalizeDocument(data)
        Map componentsMeta = normalizeComponents(data.components)

        def byId = indexNodesByIdAndKey(document)
        def resolvedComponents = [:] as Map<String, Map>

        if (componentsMeta) {
            componentsMeta.keySet().each { cid ->
                def n = byId[cid]
                if (n) resolvedComponents[cid as String] = (Map) n
            }
        }

        // Also add all COMPONENT nodes to resolution map by their id and key
        def addAllComponents
        addAllComponents = { Map n ->
            if (n?.type == 'COMPONENT' || n?.type == 'COMPONENT_SET') {
                if (n.id) resolvedComponents[n.id as String] = n
                if (n.key) resolvedComponents[n.key as String] = n
            }
            (n.children ?: []).each { addAllComponents((Map) it) }
        }
        if (document) addAllComponents(document as Map)

        // Detect missing components: track separately ids and keys for better fetch strategy
        def missingIds = [] as Set
        def missingKeys = [] as Set
        def scan
        scan = { Map node ->
            if (node.type == 'INSTANCE') {
                def cid = node.componentId ?: node.mainComponentId
                def ckey = node.componentKey ?: node.mainComponentKey
                if (cid && !resolvedComponents.containsKey(cid as String)) missingIds << (cid as String)
                if (ckey && !resolvedComponents.containsKey(ckey as String)) missingKeys << (ckey as String)
            }
            (node.children ?: []).each { scan((Map) it) }
        }
        if (document) scan(document as Map)

        // First try to resolve by keys (works across libraries)
        String envToken = (opts.figmaToken ?: System.getenv('FIGMA_TOKEN')) as String
        if (!missingKeys.isEmpty() && envToken) {
            def fetchedByKey = fetchComponentNodesByKeys(missingKeys as List<String>, envToken)
            // Merge into resolvedComponents keyed by key and also by node id
            fetchedByKey.each { String key, Map compNode ->
                resolvedComponents[key] = compNode
                if (compNode?.id) resolvedComponents[compNode.id as String] = compNode
            }
            // Recompute missingKeys after fetch
            def stillMissingKeys = [] as Set
            missingKeys.each { k -> if (!resolvedComponents.containsKey(k as String)) stillMissingKeys << k }
            missingKeys = stillMissingKeys
        }

        // Then, try to resolve remaining ids within the current file (if any) — uncommon when missing from document
        String envFileKey = (opts.figmaFileKey ?: System.getenv('FIGMA_FILE_KEY')) as String
        if (!missingIds.isEmpty() && envToken && envFileKey) {
            def fetchedById = fetchComponentNodesIfPossible(missingIds as List<String>, envFileKey, envToken)
            resolvedComponents.putAll(fetchedById)
            def stillMissingIds = [] as Set
            missingIds.each { cid -> if (!resolvedComponents.containsKey(cid as String)) stillMissingIds << cid }
            missingIds = stillMissingIds
        }

        def roots = collectRoots(document)
        // htmlByName now is a map of filename -> html content
        def htmlByName = [:] as Map<String, String>

        roots.each { r ->
            // Skip PAGE-level outputs; keep only screens (frames/groups/components/instances)
            if ((r.type as String) == 'PAGE') return

            Map node = (Map) r.node
            Map rootBBox = computeBBox(node)
            BigDecimal originX = (rootBBox.minX ?: 0) as BigDecimal
            BigDecimal originY = (rootBBox.minY ?: 0) as BigDecimal

            String inner = renderNode(node, resolvedComponents, uniformScale, uniformScale, originX, originY, 0, new HashSet<>())
            String html = wrapHtml(node, rootBBox, inner, uniformScale)

            // Derive file name using only the screen/frame name, dropping any page prefix
            String rawName = node.name ?: (r.name ?: 'Screen')
            // If a composed name came through (e.g., "Page_1__Home"), take the segment after the last "__"
            if (rawName.contains('__')) rawName = rawName.split(/__/).last()
            String baseName = sanitize(rawName?.replaceAll('[^a-zA-Z0-9]', '')) + ".html"

            htmlByName[baseName] = html
        }

        def missingAll = [] as Set
        missingAll.addAll(missingIds)
        missingAll.addAll(missingKeys)
        def possibleCalls = (missingAll as List).collect { cidOrKey -> cidOrKey.size() > 40 ? "GET /v1/components/${cidOrKey}" : "GET /v1/files/{fileKey}/nodes?ids=${cidOrKey}" }
        return [
                htmlByName         : htmlByName,
                missingComponentIds: missingAll as List,
                possibleCalls      : possibleCalls,
                resolvedComponents : resolvedComponents.keySet()
        ]
    }


    Map processFigmaForCalls(Map<String, Object> data) {
        def components = data.components ?: [:]
        def document = data.document ?: [:]

        def missing = [] as Set
        def visit
        visit = { Map node ->
            if (node.type == 'INSTANCE' && node.componentId) {
                if (!components.containsKey(node.componentId)) {
                    missing << node.componentId
                }
            }
            (node.children ?: []).each { visit(it as Map) }
        }
        visit(document as Map)

        def possibleCalls = (missing as List).collect { cid -> "GET /v1/files/{fileKey}/nodes?ids=${cid}" }

        return [missingComponentIds: missing as List, possibleCalls: possibleCalls]
    }

}
