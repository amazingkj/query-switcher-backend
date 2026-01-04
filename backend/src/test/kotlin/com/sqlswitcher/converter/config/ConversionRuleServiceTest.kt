package com.sqlswitcher.converter.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * ConversionRuleService 단위 테스트
 */
class ConversionRuleServiceTest {

    private lateinit var ruleService: ConversionRuleService

    @BeforeEach
    fun setup() {
        ruleService = ConversionRuleService()
    }

    @Nested
    @DisplayName("기본 설정 테스트")
    inner class DefaultConfigTest {

        @Test
        @DisplayName("기본 설정은 모든 규칙이 활성화됨")
        fun testDefaultConfigAllEnabled() {
            val config = ConversionRuleConfig.default()

            assertTrue(config.dataTypeConversion.convertVarchar2, "VARCHAR2 변환이 활성화되어야 함")
            assertTrue(config.dataTypeConversion.convertNumber, "NUMBER 변환이 활성화되어야 함")
            assertTrue(config.functionConversion.convertNvl, "NVL 변환이 활성화되어야 함")
            assertTrue(config.functionConversion.convertDecode, "DECODE 변환이 활성화되어야 함")
            assertTrue(config.ddlConversion.removeTablespace, "TABLESPACE 제거가 활성화되어야 함")
        }

        @Test
        @DisplayName("최소 설정은 필수 규칙만 활성화")
        fun testMinimalConfig() {
            val config = ConversionRuleConfig.minimal()

            assertTrue(config.dataTypeConversion.convertVarchar2, "VARCHAR2 변환이 활성화되어야 함")
            assertTrue(config.dataTypeConversion.convertNumber, "NUMBER 변환이 활성화되어야 함")
            assertFalse(config.dataTypeConversion.convertDate, "DATE 변환이 비활성화되어야 함")
            assertFalse(config.functionConversion.convertDecode, "DECODE 변환이 비활성화되어야 함")
        }

        @Test
        @DisplayName("엄격한 설정은 추가 경고 활성화")
        fun testStrictConfig() {
            val config = ConversionRuleConfig.strict()

            assertTrue(config.warningSettings.warnOnSelectStar, "SELECT * 경고가 활성화되어야 함")
            assertTrue(config.warningSettings.warnOnDeprecatedSyntax, "deprecated 경고가 활성화되어야 함")
            assertEquals(50, config.warningSettings.maxInClauseSize, "IN 절 최대 크기가 50이어야 함")
        }
    }

    @Nested
    @DisplayName("세션별 설정 테스트")
    inner class SessionConfigTest {

        @Test
        @DisplayName("세션별 설정 저장 및 조회")
        fun testSessionConfig() {
            val sessionId = "session-123"
            val customConfig = ConversionRuleConfig(
                functionConversion = FunctionRules(convertDecode = false)
            )

            ruleService.setConfigForSession(sessionId, customConfig)
            val retrievedConfig = ruleService.getConfigForSession(sessionId)

            assertFalse(retrievedConfig.functionConversion.convertDecode, "DECODE 변환이 비활성화되어야 함")
        }

        @Test
        @DisplayName("세션 설정이 없으면 기본 설정 반환")
        fun testDefaultFallback() {
            val config = ruleService.getConfigForSession("non-existent-session")

            assertNotNull(config, "설정이 null이 아니어야 함")
            assertTrue(config.functionConversion.convertDecode, "기본 설정의 DECODE 변환이 활성화되어야 함")
        }

        @Test
        @DisplayName("세션 설정 삭제")
        fun testClearSessionConfig() {
            val sessionId = "session-456"
            val customConfig = ConversionRuleConfig(
                functionConversion = FunctionRules(convertNvl = false)
            )

            ruleService.setConfigForSession(sessionId, customConfig)
            ruleService.clearSessionConfig(sessionId)

            val config = ruleService.getConfigForSession(sessionId)
            assertTrue(config.functionConversion.convertNvl, "삭제 후 기본 설정으로 돌아가야 함")
        }
    }

    @Nested
    @DisplayName("규칙 활성화 여부 확인 테스트")
    inner class RuleCheckTest {

        @Test
        @DisplayName("데이터타입 규칙 확인")
        fun testDataTypeRuleCheck() {
            val config = ConversionRuleConfig(
                dataTypeConversion = DataTypeRules(
                    convertVarchar2 = true,
                    convertNumber = false
                )
            )

            assertTrue(ruleService.isDataTypeConversionEnabled(config, DataTypeRule.VARCHAR2))
            assertFalse(ruleService.isDataTypeConversionEnabled(config, DataTypeRule.NUMBER))
        }

        @Test
        @DisplayName("함수 규칙 확인")
        fun testFunctionRuleCheck() {
            val config = ConversionRuleConfig(
                functionConversion = FunctionRules(
                    convertNvl = true,
                    convertDecode = false
                )
            )

            assertTrue(ruleService.isFunctionConversionEnabled(config, FunctionRule.NVL))
            assertFalse(ruleService.isFunctionConversionEnabled(config, FunctionRule.DECODE))
        }

        @Test
        @DisplayName("DDL 규칙 확인")
        fun testDdlRuleCheck() {
            val config = ConversionRuleConfig(
                ddlConversion = DdlRules(
                    removeTablespace = true,
                    convertTriggers = false
                )
            )

            assertTrue(ruleService.isDdlConversionEnabled(config, DdlRule.TABLESPACE))
            assertFalse(ruleService.isDdlConversionEnabled(config, DdlRule.TRIGGERS))
        }

        @Test
        @DisplayName("구문 규칙 확인")
        fun testSyntaxRuleCheck() {
            val config = ConversionRuleConfig(
                syntaxConversion = SyntaxRules(
                    convertOracleJoin = true,
                    convertPivot = false
                )
            )

            assertTrue(ruleService.isSyntaxConversionEnabled(config, SyntaxRule.ORACLE_JOIN))
            assertFalse(ruleService.isSyntaxConversionEnabled(config, SyntaxRule.PIVOT))
        }

        @Test
        @DisplayName("경고 규칙 확인")
        fun testWarningRuleCheck() {
            val config = ConversionRuleConfig(
                warningSettings = WarningSettings(
                    warnOnSelectStar = true,
                    warnOnDeprecatedSyntax = false
                )
            )

            assertTrue(ruleService.isWarningEnabled(config, WarningRule.SELECT_STAR))
            assertFalse(ruleService.isWarningEnabled(config, WarningRule.DEPRECATED_SYNTAX))
        }
    }

    @Nested
    @DisplayName("설정 빌더 테스트")
    inner class ConfigBuilderTest {

        @Test
        @DisplayName("빌더를 사용한 설정 생성")
        fun testConfigBuilder() {
            val config = ruleService.buildConfig {
                dataTypes {
                    convertVarchar2 = true
                    convertDate = false
                }
                functions {
                    convertNvl = true
                    convertDecode = false
                }
                ddl {
                    removeTablespace = true
                    convertTriggers = false
                }
                warnings {
                    warnOnSelectStar = true
                    maxInClauseSize = 50
                }
            }

            assertTrue(config.dataTypeConversion.convertVarchar2)
            assertFalse(config.dataTypeConversion.convertDate)
            assertTrue(config.functionConversion.convertNvl)
            assertFalse(config.functionConversion.convertDecode)
            assertTrue(config.ddlConversion.removeTablespace)
            assertTrue(config.warningSettings.warnOnSelectStar)
            assertEquals(50, config.warningSettings.maxInClauseSize)
        }
    }

    @Nested
    @DisplayName("규칙 조합 테스트")
    inner class RuleCombinationTest {

        @Test
        @DisplayName("데이터타입만 활성화")
        fun testDataTypeOnlyConfig() {
            val config = ConversionRuleConfig(
                dataTypeConversion = DataTypeRules(),
                functionConversion = FunctionRules(
                    convertNvl = false,
                    convertDecode = false,
                    convertDateFunctions = false
                ),
                ddlConversion = DdlRules(
                    removeTablespace = false,
                    removeStorageClause = false
                )
            )

            assertTrue(config.dataTypeConversion.convertVarchar2)
            assertFalse(config.functionConversion.convertNvl)
            assertFalse(config.ddlConversion.removeTablespace)
        }

        @Test
        @DisplayName("DDL만 활성화")
        fun testDdlOnlyConfig() {
            val config = ConversionRuleConfig(
                dataTypeConversion = DataTypeRules(
                    convertVarchar2 = false,
                    convertNumber = false
                ),
                functionConversion = FunctionRules(
                    convertNvl = false,
                    convertDecode = false
                ),
                ddlConversion = DdlRules()
            )

            assertFalse(config.dataTypeConversion.convertVarchar2)
            assertFalse(config.functionConversion.convertNvl)
            assertTrue(config.ddlConversion.removeTablespace)
        }
    }
}
