package com.sqlswitcher.converter

import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired

/**
 * 변환 규칙 테스트 케이스 및 예외 처리를 위한 서비스
 */
@Component
class ConversionTestService @Autowired constructor(
    private val sqlConverterEngine: SqlConverterEngine,
    private val functionMapper: FunctionMapper,
    private val dataTypeConverter: DataTypeConverter
) {
    
    /**
     * 변환 테스트 케이스 실행
     */
    fun runConversionTests(): TestResult {
        val testCases = initializeTestCases()
        val results = mutableListOf<TestCaseResult>()
        
        testCases.forEach { testCase ->
            try {
                val result = sqlConverterEngine.convertSql(
                    sql = testCase.sql,
                    sourceDialect = testCase.sourceDialect,
                    targetDialect = testCase.targetDialect
                )
                
                val testResult = TestCaseResult(
                    testCase = testCase,
                    result = result,
                    passed = evaluateTestResult(testCase, result),
                    error = null
                )
                results.add(testResult)
                
            } catch (e: Exception) {
                val testResult = TestCaseResult(
                    testCase = testCase,
                    result = null,
                    passed = false,
                    error = e.message
                )
                results.add(testResult)
            }
        }
        
        return TestResult(
            totalTests = testCases.size,
            passedTests = results.count { it.passed },
            failedTests = results.count { !it.passed },
            testResults = results
        )
    }
    
    /**
     * 테스트 결과 평가
     */
    private fun evaluateTestResult(testCase: ConversionTestCase, result: SqlConversionResult): Boolean {
        return when (testCase.expectedResult) {
            is ExpectedResult.ExactMatch -> {
                result.convertedSql.equals(testCase.expectedResult.expectedSql, ignoreCase = true)
            }
            is ExpectedResult.Contains -> {
                result.convertedSql.contains(testCase.expectedResult.expectedText, ignoreCase = true)
            }
            is ExpectedResult.WarningCount -> {
                result.warnings.size == testCase.expectedResult.expectedCount
            }
            is ExpectedResult.NoErrors -> {
                result.warnings.none { it.severity == WarningSeverity.ERROR }
            }
        }
    }
    
    /**
     * 테스트 케이스 초기화
     */
    private fun initializeTestCases(): List<ConversionTestCase> {
        return listOf(
            // MySQL → PostgreSQL 테스트 케이스
            ConversionTestCase(
                name = "MySQL NOW() to PostgreSQL CURRENT_TIMESTAMP",
                sql = "SELECT NOW() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.Contains("CURRENT_TIMESTAMP")
            ),
            
            ConversionTestCase(
                name = "MySQL IFNULL() to PostgreSQL COALESCE()",
                sql = "SELECT IFNULL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.Contains("COALESCE")
            ),
            
            ConversionTestCase(
                name = "MySQL DATE_FORMAT() to PostgreSQL TO_CHAR()",
                sql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.Contains("TO_CHAR")
            ),
            
            ConversionTestCase(
                name = "MySQL GROUP_CONCAT() to PostgreSQL STRING_AGG()",
                sql = "SELECT GROUP_CONCAT(name) FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.Contains("STRING_AGG")
            ),
            
            // MySQL → Oracle 테스트 케이스
            ConversionTestCase(
                name = "MySQL NOW() to Oracle SYSDATE",
                sql = "SELECT NOW() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                expectedResult = ExpectedResult.Contains("SYSDATE")
            ),
            
            ConversionTestCase(
                name = "MySQL IFNULL() to Oracle NVL()",
                sql = "SELECT IFNULL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                expectedResult = ExpectedResult.Contains("NVL")
            ),
            
            ConversionTestCase(
                name = "MySQL DATE_FORMAT() to Oracle TO_CHAR()",
                sql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                expectedResult = ExpectedResult.Contains("TO_CHAR")
            ),
            
            ConversionTestCase(
                name = "MySQL GROUP_CONCAT() to Oracle LISTAGG()",
                sql = "SELECT GROUP_CONCAT(name) FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                expectedResult = ExpectedResult.Contains("LISTAGG")
            ),
            
            // PostgreSQL → MySQL 테스트 케이스
            ConversionTestCase(
                name = "PostgreSQL CURRENT_TIMESTAMP to MySQL NOW()",
                sql = "SELECT CURRENT_TIMESTAMP FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("NOW")
            ),
            
            ConversionTestCase(
                name = "PostgreSQL COALESCE() to MySQL IFNULL()",
                sql = "SELECT COALESCE(name, 'Unknown') FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("IFNULL")
            ),
            
            ConversionTestCase(
                name = "PostgreSQL TO_CHAR() to MySQL DATE_FORMAT()",
                sql = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("DATE_FORMAT")
            ),
            
            ConversionTestCase(
                name = "PostgreSQL STRING_AGG() to MySQL GROUP_CONCAT()",
                sql = "SELECT STRING_AGG(name, ',') FROM users",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("GROUP_CONCAT")
            ),
            
            // Oracle → MySQL 테스트 케이스
            ConversionTestCase(
                name = "Oracle SYSDATE to MySQL NOW()",
                sql = "SELECT SYSDATE FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("NOW")
            ),
            
            ConversionTestCase(
                name = "Oracle NVL() to MySQL IFNULL()",
                sql = "SELECT NVL(name, 'Unknown') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("IFNULL")
            ),
            
            ConversionTestCase(
                name = "Oracle TO_CHAR() to MySQL DATE_FORMAT()",
                sql = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("DATE_FORMAT")
            ),
            
            ConversionTestCase(
                name = "Oracle LISTAGG() to MySQL GROUP_CONCAT()",
                sql = "SELECT LISTAGG(name, ',') FROM users",
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.Contains("GROUP_CONCAT")
            ),
            
            // 복잡한 쿼리 테스트 케이스
            ConversionTestCase(
                name = "Complex MySQL query with multiple functions",
                sql = "SELECT u.id, IFNULL(u.name, 'Unknown'), DATE_FORMAT(u.created_at, '%Y-%m-%d'), GROUP_CONCAT(r.name) FROM users u LEFT JOIN user_roles ur ON u.id = ur.user_id LEFT JOIN roles r ON ur.role_id = r.id WHERE u.created_at > NOW() - INTERVAL 30 DAY GROUP BY u.id",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.NoErrors
            ),
            
            ConversionTestCase(
                name = "Complex PostgreSQL query with multiple functions",
                sql = "SELECT u.id, COALESCE(u.name, 'Unknown'), TO_CHAR(u.created_at, 'YYYY-MM-DD'), STRING_AGG(r.name, ',') FROM users u LEFT JOIN user_roles ur ON u.id = ur.user_id LEFT JOIN roles r ON ur.role_id = r.id WHERE u.created_at > CURRENT_TIMESTAMP - INTERVAL '30 days' GROUP BY u.id",
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                expectedResult = ExpectedResult.NoErrors
            ),
            
            // 오류 처리 테스트 케이스
            ConversionTestCase(
                name = "Invalid SQL syntax",
                sql = "SELECT * FROM users WHERE",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.WarningCount(1)
            ),
            
            ConversionTestCase(
                name = "Unsupported function",
                sql = "SELECT UNSUPPORTED_FUNCTION() FROM users",
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                expectedResult = ExpectedResult.WarningCount(1)
            )
        )
    }
    
    /**
     * 함수 매핑 테스트
     */
    fun testFunctionMappings(): FunctionMappingTestResult {
        val testCases = listOf(
            FunctionMappingTestCase(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceFunction = "NOW",
                expectedTargetFunction = "CURRENT_TIMESTAMP"
            ),
            FunctionMappingTestCase(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceFunction = "IFNULL",
                expectedTargetFunction = "NVL"
            ),
            FunctionMappingTestCase(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "COALESCE",
                expectedTargetFunction = "IFNULL"
            ),
            FunctionMappingTestCase(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceFunction = "SYSDATE",
                expectedTargetFunction = "NOW"
            )
        )
        
        val results = testCases.map { testCase ->
            val function = net.sf.jsqlparser.expression.Function().apply {
                name = testCase.sourceFunction
            }
            
            val mappingResult = functionMapper.mapFunction(
                sourceDialect = testCase.sourceDialect,
                targetDialect = testCase.targetDialect,
                function = function
            )
            
            FunctionMappingTestCaseResult(
                testCase = testCase,
                actualTargetFunction = mappingResult.mappedFunction.name,
                passed = mappingResult.mappedFunction.name.equals(testCase.expectedTargetFunction, ignoreCase = true),
                warnings = mappingResult.warnings
            )
        }
        
        return FunctionMappingTestResult(
            totalTests = testCases.size,
            passedTests = results.count { it.passed },
            failedTests = results.count { !it.passed },
            testResults = results
        )
    }
    
    /**
     * 데이터 타입 변환 테스트
     */
    fun testDataTypeConversions(): DataTypeConversionTestResult {
        val testCases = listOf(
            DataTypeConversionTestCase(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.POSTGRESQL,
                sourceType = "TINYINT",
                expectedTargetType = "SMALLINT"
            ),
            DataTypeConversionTestCase(
                sourceDialect = DialectType.MYSQL,
                targetDialect = DialectType.ORACLE,
                sourceType = "VARCHAR",
                expectedTargetType = "VARCHAR2"
            ),
            DataTypeConversionTestCase(
                sourceDialect = DialectType.POSTGRESQL,
                targetDialect = DialectType.MYSQL,
                sourceType = "SERIAL",
                expectedTargetType = "AUTO_INCREMENT"
            ),
            DataTypeConversionTestCase(
                sourceDialect = DialectType.ORACLE,
                targetDialect = DialectType.MYSQL,
                sourceType = "NUMBER",
                expectedTargetType = "DECIMAL"
            )
        )
        
        val results = testCases.map { testCase ->
            val conversionResult = dataTypeConverter.convertDataType(
                sourceDialect = testCase.sourceDialect,
                targetDialect = testCase.targetDialect,
                dataType = testCase.sourceType
            )
            
            DataTypeConversionTestCaseResult(
                testCase = testCase,
                actualTargetType = conversionResult.convertedType,
                passed = conversionResult.convertedType.equals(testCase.expectedTargetType, ignoreCase = true),
                warnings = conversionResult.warnings
            )
        }
        
        return DataTypeConversionTestResult(
            totalTests = testCases.size,
            passedTests = results.count { it.passed },
            failedTests = results.count { !it.passed },
            testResults = results
        )
    }
}

/**
 * 변환 테스트 케이스
 */
data class ConversionTestCase(
    val name: String,
    val sql: String,
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val expectedResult: ExpectedResult
)

/**
 * 예상 결과 타입
 */
sealed class ExpectedResult {
    data class ExactMatch(val expectedSql: String) : ExpectedResult()
    data class Contains(val expectedText: String) : ExpectedResult()
    data class WarningCount(val expectedCount: Int) : ExpectedResult()
    object NoErrors : ExpectedResult()
}

/**
 * 테스트 케이스 결과
 */
data class TestCaseResult(
    val testCase: ConversionTestCase,
    val result: SqlConversionResult?,
    val passed: Boolean,
    val error: String?
)

/**
 * 테스트 결과
 */
data class TestResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<TestCaseResult>
)

/**
 * 함수 매핑 테스트 케이스
 */
data class FunctionMappingTestCase(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceFunction: String,
    val expectedTargetFunction: String
)

/**
 * 함수 매핑 테스트 케이스 결과
 */
data class FunctionMappingTestCaseResult(
    val testCase: FunctionMappingTestCase,
    val actualTargetFunction: String?,
    val passed: Boolean,
    val warnings: List<ConversionWarning>
)

/**
 * 함수 매핑 테스트 결과
 */
data class FunctionMappingTestResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<FunctionMappingTestCaseResult>
)

/**
 * 데이터 타입 변환 테스트 케이스
 */
data class DataTypeConversionTestCase(
    val sourceDialect: DialectType,
    val targetDialect: DialectType,
    val sourceType: String,
    val expectedTargetType: String
)

/**
 * 데이터 타입 변환 테스트 케이스 결과
 */
data class DataTypeConversionTestCaseResult(
    val testCase: DataTypeConversionTestCase,
    val actualTargetType: String,
    val passed: Boolean,
    val warnings: List<ConversionWarning>
)

/**
 * 데이터 타입 변환 테스트 결과
 */
data class DataTypeConversionTestResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<DataTypeConversionTestCaseResult>
)
