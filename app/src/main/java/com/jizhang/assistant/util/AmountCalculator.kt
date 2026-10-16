package com.jizhang.assistant.util

/**
 * 记账金额计算器。
 *
 * 支持形如 "12.5+3×2-1÷4" 的中缀表达式，按运算符优先级求值。
 * 状态由 [expression] 字符串承载，UI 每次按键追加/删除后调用 [result]。
 */
class AmountCalculator {

    var expression: String = ""
        private set

    /** 追加一个 token（数字、'.'、运算符） */
    fun append(token: String) {
        if (token.isEmpty()) return
        val expr = expression
        val last = expr.lastOrNull()

        when {
            token[0].isDigit() -> {
                // 防止数字段出现两个小数点
                val currentNumber = currentNumber(expr)
                if (token == "." && currentNumber.contains('.')) return
                // 整数部分限制长度
                if (token[0].isDigit() && currentNumber.length >= 12) return
                if (expr == "0" && token[0].isDigit()) expression = token
                else expression = expr + token
            }
            token == "." -> {
                val currentNumber = currentNumber(expr)
                if (currentNumber.contains('.')) return
                expression = if (currentNumber.isEmpty()) "$expr 0." else "$expr."
            }
            token.length == 1 && token[0] in OPERATORS -> {
                if (expr.isEmpty()) return
                if (last == null) return
                if (last in OPERATORS) expression = expr.dropLast(1) + token
                else expression = expr + token
            }
        }
    }

    /** 退格 */
    fun backspace() {
        expression = expression.dropLast(1)
    }

    /** 清空 */
    fun clear() { expression = "" }

    /** 当前可显示的预览结果（不修改表达式） */
    fun preview(): String {
        val value = evaluate(expression)
        return if (value == null) "" else formatResult(value)
    }

    /** 最终金额（完成记账时取值），非法返回 null */
    fun result(): Double? {
        val expr = expression.trim()
        if (expr.isEmpty() || expr.last() in OPERATORS) return null
        val value = evaluate(expr) ?: return null
        return if (value > 0.0 && value < 1e12) round2(value) else null
    }

    private fun currentNumber(expr: String): String {
        var i = expr.length - 1
        while (i >= 0 && expr[i] !in OPERATORS) i--
        return expr.substring(i + 1)
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    private fun formatResult(v: Double): String {
        val r = round2(v)
        return if (r.toLong().toDouble() == r) r.toLong().toString() else r.toString()
    }

    companion object {
        private val OPERATORS = charArrayOf('+', '-', '×', '÷')

        /** 中缀表达式求值（支持 + - × ÷ 与优先级） */
        fun evaluate(exprRaw: String): Double? {
            val expr = exprRaw.trim().replace('×', '*').replace('÷', '/')
            if (expr.isEmpty()) return null
            // 词法
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < expr.length) {
                val c = expr[i]
                when {
                    c.isDigit() || c == '.' -> {
                        val sb = StringBuilder()
                        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                            sb.append(expr[i]); i++
                        }
                        val num = sb.toString()
                        if (num.count { it == '.' } > 1) return null
                        tokens.add(num)
                    }
                    c == '+' || c == '-' || c == '*' || c == '/' -> {
                        tokens.add(c.toString()); i++
                    }
                    c == ' ' -> i++
                    else -> return null
                }
            }
            if (tokens.isEmpty() || tokens.first().length > 1 &&
                tokens.first().toDoubleOrNull() == null
            ) return null
            // 语法检查：数字与运算符交替，以数字开头结尾
            if (tokens.first() in listOf("+", "-", "*", "/")) return null
            if (tokens.last() in listOf("+", "-", "*", "/")) return null
            var expectNumber = true
            for (t in tokens) {
                val isOp = t in listOf("+", "-", "*", "/")
                if (expectNumber == isOp) return null
                expectNumber = !expectNumber
            }
            // 先算 × ÷
            val pass1 = mutableListOf<String>()
            var idx = 0
            while (idx < tokens.size) {
                val t = tokens[idx]
                if (t == "*" || t == "/") {
                    val left = pass1.removeAt(pass1.size - 1).toDoubleOrNull() ?: return null
                    val right = tokens.getOrNull(idx + 1)?.toDoubleOrNull() ?: return null
                    if (t == "/" && right == 0.0) return null
                    val v = if (t == "*") left * right else left / right
                    pass1.add(formatNum(v)); idx += 2
                } else {
                    pass1.add(t); idx++
                }
            }
            // 再算 + -
            var acc = pass1.first().toDoubleOrNull() ?: return null
            var j = 1
            while (j < pass1.size) {
                val op = pass1[j]
                val num = pass1.getOrNull(j + 1)?.toDoubleOrNull() ?: return null
                acc = when (op) {
                    "+" -> acc + num
                    "-" -> acc - num
                    else -> return null
                }
                j += 2
            }
            return acc
        }

        private fun formatNum(v: Double): String =
            if (v.toLong().toDouble() == v) v.toLong().toString() else v.toString()
    }
}
