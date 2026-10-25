package com.codync.android.markdown
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal object SelectionSpanReflect {

    @Volatile private var localResolved = false
    @Volatile private var localSelectionRegistrar: Any? = null
    @Volatile private var compositionLocalGetCurrent: Method? = null

    fun currentSelectionRegistrar(composer: Any?): Any? {
        if (composer == null) return null
        return runCatching {
            ensureLocalResolved()
            val local = localSelectionRegistrar ?: return null
            compositionLocalGetCurrent?.invoke(local, composer, 0)
        }.getOrNull()
    }

    private fun ensureLocalResolved() {
        if (localResolved) return
        synchronized(this) {
            if (localResolved) return
            runCatching {
                val kt = Class.forName("androidx.compose.foundation.text.selection.SelectionRegistrarKt")
                val accessor = kt.methods.first { it.name == "getLocalSelectionRegistrar" }
                localSelectionRegistrar = accessor.invoke(null)
                val clClass = Class.forName("androidx.compose.runtime.CompositionLocal")
                compositionLocalGetCurrent = clClass.methods.first { it.name == "getCurrent" }
            }
            localResolved = true
        }
    }

    @Volatile private var resolved = false
    @Volatile private var getSubselections: Method? = null
    @Volatile private var getSelectableMap: Method? = null
    @Volatile private var longObjectMapGet: Method? = null
    @Volatile private var longObjectMapForEach: Method? = null
    @Volatile private var selectableGetText: Method? = null
    @Volatile private var selectionGetStart: Method? = null
    @Volatile private var selectionGetEnd: Method? = null
    @Volatile private var anchorGetOffset: Method? = null
    @Volatile private var anchorGetSelectableId: Method? = null

    fun extractSelectedText(registrar: Any?): String? {
        if (registrar == null) return null
        return runCatching { extract(registrar) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun extract(registrar: Any): String? {
        val spans = extractSpansInternal(registrar) ?: return null
        return spans.joinToString("\n") { it.text.substring(it.startOffset, it.endOffset) }
            .trim()
            .ifBlank { null }
    }

    data class RawSpan(val text: String, val startOffset: Int, val endOffset: Int)

    fun extractSelectedSpans(registrar: Any?): List<RawSpan>? {
        if (registrar == null) return null
        return runCatching { extractSpansInternal(registrar) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun extractSpansInternal(registrar: Any): List<RawSpan>? {
        ensureResolved(registrar.javaClass)
        val subs = getSubselections?.invoke(registrar) ?: return null
        val selectableMap = getSelectableMap?.invoke(registrar) ?: return null
        val entries = ArrayList<Pair<Long, Any>>()
        val forEach = longObjectMapForEach ?: return null
        val consumer = Proxy.newProxyInstance(
            registrar.javaClass.classLoader,
            arrayOf(Class.forName("kotlin.jvm.functions.Function2")),
        ) { _, _, args ->
            val id = (args?.getOrNull(0) as? Number)?.toLong()
            val sel = args?.getOrNull(1)
            if (id != null && sel != null) entries.add(id to sel)
            Unit
        }
        forEach.invoke(subs, consumer)
        if (entries.isEmpty()) return null
        val get = longObjectMapGet
        val spans = ArrayList<RawSpan>()
        for ((id, sel) in entries.sortedBy { it.first }) {
            val selectable = get?.invoke(selectableMap, id) ?: continue
            val annotated = selectableGetText?.invoke(selectable) ?: continue
            val text = annotated.toString()
            val start = sel.let { anchorGetOffset?.invoke(selectionGetStart?.invoke(it)) } as? Int ?: continue
            val end = sel.let { anchorGetOffset?.invoke(selectionGetEnd?.invoke(it)) } as? Int ?: continue
            val a = start.coerceIn(0, text.length)
            val b = end.coerceIn(0, text.length)
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            if (lo < hi) spans.add(RawSpan(text = text, startOffset = lo, endOffset = hi))
        }
        return spans
    }

    private fun ensureResolved(registrarClass: Class<*>) {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            runCatching {
                getSubselections = registrarClass.methods.firstOrNull { it.name == "getSubselections" }
                getSelectableMap = registrarClass.methods.firstOrNull {

                    it.name == "getSelectableMap" || it.name.startsWith("getSelectableMap\$")
                }

                val longObjectMapClass = getSubselections?.returnType
                    ?: Class.forName("androidx.collection.LongObjectMap")
                longObjectMapGet = longObjectMapClass.methods.firstOrNull {
                    it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Long::class.javaPrimitiveType
                }
                longObjectMapForEach = longObjectMapClass.methods.firstOrNull {
                    it.name == "forEach" && it.parameterTypes.size == 1
                }

                val selectableClass = Class.forName("androidx.compose.foundation.text.selection.Selectable")
                selectableGetText = selectableClass.methods.firstOrNull { it.name == "getText" }

                val selectionClass = Class.forName("androidx.compose.foundation.text.selection.Selection")
                selectionGetStart = selectionClass.methods.firstOrNull { it.name == "getStart" }
                selectionGetEnd = selectionClass.methods.firstOrNull { it.name == "getEnd" }

                val anchorClass = Class.forName("androidx.compose.foundation.text.selection.Selection\$AnchorInfo")
                anchorGetOffset = anchorClass.methods.firstOrNull { it.name == "getOffset" }
                anchorGetSelectableId = anchorClass.methods.firstOrNull { it.name == "getSelectableId" }
            }
            resolved = true
        }
    }
}
