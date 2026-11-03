package ua.rytm.app.ui.screens.finance

/**
 * The amount field doubles as a calculator: "2 × 70", "500 − 120",
 * "1 200 / 3". × and ÷ bind tighter than + and −; the result is rounded to
 * kopecks. A plain number goes through [parseMoneyInput] unchanged, so
 * nothing that parsed before parses differently now.
 */
object AmountExpression {
    /** Operator glyphs as the buttons type them. */
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'

    private fun normalize(c: Char): Char? = when (c) {
        '+' -> '+'
        '-', '−', '–' -> '-'
        '*', '×', 'x', 'х' -> '*'
        '/', '÷', ':' -> '/'
        else -> null
    }

    /** True when the text holds an operator after its first character. */
    fun isExpression(text: String): Boolean = text.trim().drop(1).any { normalize(it) != null }

    /** Characters the field accepts. */
    fun isAllowed(c: Char): Boolean = c.isDigit() || c == '.' || c == ',' || c == ' ' || c == ' ' || normalize(c) != null

    fun evaluate(text: String): Double? {
        if (!isExpression(text)) return parseMoneyInput(text)
        val numbers = mutableListOf<Double>()
        val ops = mutableListOf<Char>()
        val current = StringBuilder()
        for (c in text.trim()) {
            val op = normalize(c)
            if (op == null) { current.append(c); continue }
            numbers += parseMoneyInput(current.toString()) ?: return null
            ops += op
            current.clear()
        }
        numbers += parseMoneyInput(current.toString()) ?: return null
        // × and ÷ first, left to right.
        var i = 0
        while (i < ops.size) {
            val op = ops[i]
            if (op == '*' || op == '/') {
                val right = numbers[i + 1]
                if (op == '/' && right == 0.0) return null
                numbers[i] = if (op == '*') numbers[i] * right else numbers[i] / right
                numbers.removeAt(i + 1)
                ops.removeAt(i)
            } else i++
        }
        var result = numbers[0]
        ops.forEachIndexed { k, op -> result = if (op == '+') result + numbers[k + 1] else result - numbers[k + 1] }
        return (Math.round(result * 100) / 100.0).takeIf { it.isFinite() }
    }

    /**
     * The text after an operator button: replaces a trailing operator instead
     * of stacking two, and ignores the press on an empty field.
     */
    fun append(text: String, op: Char): String {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return text
        val base = if (normalize(trimmed.last()) != null) trimmed.dropLast(1).trimEnd() else trimmed
        return "$base $op "
    }
}
