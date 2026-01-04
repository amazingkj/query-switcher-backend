package com.sqlswitcher.converter.util

/**
 * SQL 파싱 관련 공통 유틸리티
 */
object SqlParsingUtils {

    /**
     * 함수 시작 위치에서 괄호 매칭으로 함수 끝 위치 찾기
     * (문자열 리터럴 내부의 괄호는 무시)
     * @param sql SQL 문자열
     * @param argsStartIdx 여는 괄호 다음 인덱스
     * @return 닫는 괄호 다음 인덱스, 또는 매칭 실패 시 -1
     */
    fun findMatchingBracket(sql: String, argsStartIdx: Int): Int {
        var depth = 1
        var idx = argsStartIdx
        var inSingleQuote = false
        var inDoubleQuote = false

        while (idx < sql.length && depth > 0) {
            val char = sql[idx]

            // 문자열 리터럴 처리
            if (char == '\'' && !inDoubleQuote) {
                // 이스케이프된 작은따옴표 확인 ('')
                if (inSingleQuote && idx + 1 < sql.length && sql[idx + 1] == '\'') {
                    idx += 2
                    continue
                }
                inSingleQuote = !inSingleQuote
            } else if (char == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
            } else if (!inSingleQuote && !inDoubleQuote) {
                when (char) {
                    '(' -> depth++
                    ')' -> depth--
                }
            }
            idx++
        }
        return if (depth == 0) idx else -1
    }

    /**
     * 함수 인자를 콤마로 분리 (괄호 내부 및 문자열 리터럴 내부의 콤마는 무시)
     */
    fun splitFunctionArgs(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < argsStr.length) {
            val char = argsStr[i]

            // 문자열 리터럴 처리
            if (char == '\'' && !inDoubleQuote) {
                // 이스케이프된 작은따옴표 확인 ('')
                if (inSingleQuote && i + 1 < argsStr.length && argsStr[i + 1] == '\'') {
                    current.append("''")
                    i += 2
                    continue
                }
                inSingleQuote = !inSingleQuote
                current.append(char)
            } else if (char == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                current.append(char)
            } else if (inSingleQuote || inDoubleQuote) {
                // 문자열 리터럴 내부는 그대로 추가
                current.append(char)
            } else {
                when (char) {
                    '(' -> {
                        depth++
                        current.append(char)
                    }
                    ')' -> {
                        depth--
                        current.append(char)
                    }
                    ',' -> {
                        if (depth == 0) {
                            args.add(current.toString().trim())
                            current = StringBuilder()
                        } else {
                            current.append(char)
                        }
                    }
                    else -> current.append(char)
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            args.add(current.toString().trim())
        }

        return args
    }

    /**
     * || 앞의 표현식과 시작 인덱스 추출
     * @return Pair(표현식, 표현식 시작 인덱스)
     */
    fun extractExpressionBeforeWithIndex(sql: String, pipeIdx: Int): Pair<String, Int> {
        var idx = pipeIdx - 1
        // 공백 건너뛰기
        while (idx >= 0 && sql[idx].isWhitespace()) idx--
        if (idx < 0) return Pair("", 0)

        val endIdx = idx + 1

        // 문자열 리터럴인 경우
        if (sql[idx] == '\'') {
            idx--
            while (idx >= 0 && sql[idx] != '\'') idx--
            if (idx < 0) return Pair("", 0)
            return Pair(sql.substring(idx, endIdx), idx)
        }

        // 괄호로 끝나는 경우 (함수 호출)
        if (sql[idx] == ')') {
            var depth = 1
            idx--
            while (idx >= 0 && depth > 0) {
                when (sql[idx]) {
                    ')' -> depth++
                    '(' -> depth--
                }
                idx--
            }
            // 함수명 추출
            while (idx >= 0 && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx--
            return Pair(sql.substring(idx + 1, endIdx), idx + 1)
        }

        // 일반 식별자 (컬럼명, 테이블.컬럼명)
        while (idx >= 0 && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx--
        return Pair(sql.substring(idx + 1, endIdx), idx + 1)
    }

    /**
     * || 뒤의 표현식과 끝 인덱스 추출
     * @return Pair(표현식, 표현식 끝 인덱스)
     */
    fun extractExpressionAfterWithIndex(sql: String, startIdx: Int): Pair<String, Int> {
        var idx = startIdx
        // 공백 건너뛰기
        while (idx < sql.length && sql[idx].isWhitespace()) idx++
        if (idx >= sql.length) return Pair("", startIdx)

        val beginIdx = idx

        // 문자열 리터럴인 경우
        if (sql[idx] == '\'') {
            idx++
            while (idx < sql.length && sql[idx] != '\'') idx++
            if (idx >= sql.length) return Pair("", startIdx)
            return Pair(sql.substring(beginIdx, idx + 1), idx + 1)
        }

        // 괄호로 시작하는 경우 (함수 호출 또는 서브쿼리)
        if (sql[idx] == '(') {
            var depth = 1
            idx++
            while (idx < sql.length && depth > 0) {
                when (sql[idx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                idx++
            }
            return Pair(sql.substring(beginIdx, idx), idx)
        }

        // 일반 식별자 (컬럼명, 테이블.컬럼명, 함수호출)
        while (idx < sql.length && (sql[idx].isLetterOrDigit() || sql[idx] == '_' || sql[idx] == '.')) idx++

        // 함수 호출인 경우 괄호까지 포함
        if (idx < sql.length && sql[idx] == '(') {
            var depth = 1
            idx++
            while (idx < sql.length && depth > 0) {
                when (sql[idx]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                idx++
            }
        }

        return Pair(sql.substring(beginIdx, idx), idx)
    }
}