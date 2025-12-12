import React, { useState } from 'react';
import { useSqlValidate, useSqlTest, useContainerStatus } from '../hooks/useSqlValidation';
import type { DialectType, ValidationResponse, TestResponse } from '../types';

interface SqlValidationPanelProps {
  sql: string;
  dialect: DialectType;
  isOpen: boolean;
  onClose: () => void;
}

export const SqlValidationPanel: React.FC<SqlValidationPanelProps> = ({
  sql,
  dialect,
  isOpen,
  onClose
}) => {
  const [validationResult, setValidationResult] = useState<ValidationResponse | null>(null);
  const [testResult, setTestResult] = useState<TestResponse | null>(null);
  const [dryRun, setDryRun] = useState(true);

  const validateMutation = useSqlValidate();
  const testMutation = useSqlTest();
  const { data: containerStatus } = useContainerStatus();

  const handleValidate = () => {
    setValidationResult(null);
    validateMutation.mutate(
      { sql, dialect },
      {
        onSuccess: (data) => {
          setValidationResult(data);
        }
      }
    );
  };

  const handleTest = () => {
    setTestResult(null);
    testMutation.mutate(
      { sql, dialect, dryRun },
      {
        onSuccess: (data) => {
          setTestResult(data);
        }
      }
    );
  };

  if (!isOpen) return null;

  const isContainerRunning = containerStatus?.containers?.[dialect]?.running ?? false;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-3 sm:p-4">
      <div className="bg-white w-full max-w-2xl max-h-[90vh] overflow-y-auto shadow-xl rounded-lg">
        {/* 헤더 */}
        <div className="flex items-center justify-between px-4 sm:px-6 py-3 sm:py-4 border-b border-gray-200 bg-gradient-to-r from-teal-50 to-cyan-50">
          <div className="flex items-center gap-2 sm:gap-3">
            <div className="p-1.5 sm:p-2 bg-teal-100 rounded-lg">
              <svg className="w-4 h-4 sm:w-5 sm:h-5 text-teal-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-semibold text-gray-900">SQL 검증</h2>
              <p className="text-xs text-gray-500 hidden sm:block">변환된 SQL의 유효성을 검증합니다</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 sm:p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 컨텐츠 */}
        <div className="p-4 sm:p-6 space-y-4 sm:space-y-6">
          {/* SQL 미리보기 */}
          <div>
            <h3 className="text-xs sm:text-sm font-medium text-gray-700 mb-2">검증할 SQL ({dialect})</h3>
            <div className="bg-gray-900 text-gray-100 p-3 sm:p-4 rounded-lg text-xs sm:text-sm font-mono max-h-32 sm:max-h-40 overflow-y-auto">
              <pre className="whitespace-pre-wrap">{sql || '(SQL이 없습니다)'}</pre>
            </div>
          </div>

          {/* 검증 옵션 */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
            {/* 문법 검증 */}
            <div className="p-3 sm:p-4 border border-gray-200 rounded-lg">
              <div className="flex items-center gap-2 mb-2 sm:mb-3">
                <svg className="w-4 h-4 sm:w-5 sm:h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                <h4 className="text-sm sm:text-base font-medium text-gray-900">문법 검증</h4>
              </div>
              <p className="text-xs text-gray-500 mb-2 sm:mb-3 hidden sm:block">
                JSQLParser로 SQL 문법을 빠르게 검증합니다. DB 연결 없이 동작합니다.
              </p>
              <button
                onClick={handleValidate}
                disabled={!sql || validateMutation.isPending}
                className="w-full px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg transition-colors"
              >
                {validateMutation.isPending ? '검증 중...' : '문법 검증'}
              </button>
            </div>

            {/* 실제 테스트 */}
            <div className="p-3 sm:p-4 border border-gray-200 rounded-lg">
              <div className="flex items-center gap-2 mb-2 sm:mb-3">
                <svg className="w-4 h-4 sm:w-5 sm:h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
                </svg>
                <h4 className="text-sm sm:text-base font-medium text-gray-900">실제 DB 테스트</h4>
              </div>
              <p className="text-xs text-gray-500 mb-2 hidden sm:block">
                Docker 컨테이너에서 실제로 SQL을 실행합니다.
              </p>
              <div className="flex items-center gap-2 mb-2 sm:mb-3">
                <span className={`inline-flex items-center px-2 py-0.5 text-xs rounded-full ${
                  isContainerRunning
                    ? 'bg-green-100 text-green-700'
                    : 'bg-gray-100 text-gray-600'
                }`}>
                  <span className={`w-1.5 h-1.5 rounded-full mr-1.5 ${
                    isContainerRunning ? 'bg-green-500' : 'bg-gray-400'
                  }`}></span>
                  {isContainerRunning ? '실행 중' : '대기 중'}
                </span>
              </div>
              <label className="flex items-center gap-2 text-xs text-gray-600 mb-2 sm:mb-3">
                <input
                  type="checkbox"
                  checked={dryRun}
                  onChange={(e) => setDryRun(e.target.checked)}
                  className="w-3 h-3 text-green-600 border-gray-300 rounded focus:ring-green-500"
                />
                DryRun (롤백)
              </label>
              <button
                onClick={handleTest}
                disabled={!sql || testMutation.isPending}
                className="w-full px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium text-white bg-green-600 hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed rounded-lg transition-colors"
              >
                {testMutation.isPending ? '테스트 중...' : '실제 테스트'}
              </button>
            </div>
          </div>

          {/* 문법 검증 결과 */}
          {validationResult && (
            <div className={`p-4 rounded-lg border ${
              validationResult.isValid
                ? 'bg-green-50 border-green-200'
                : 'bg-red-50 border-red-200'
            }`}>
              <div className="flex items-center gap-2 mb-3">
                {validationResult.isValid ? (
                  <svg className="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                ) : (
                  <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                )}
                <h4 className={`font-medium ${validationResult.isValid ? 'text-green-800' : 'text-red-800'}`}>
                  문법 검증 {validationResult.isValid ? '성공' : '실패'}
                </h4>
                {validationResult.parsedStatementType && (
                  <span className="px-2 py-0.5 text-xs bg-blue-100 text-blue-700 rounded">
                    {validationResult.parsedStatementType}
                  </span>
                )}
              </div>

              {/* 에러 목록 */}
              {validationResult.errors.length > 0 && (
                <div className="space-y-2 mb-3">
                  <h5 className="text-sm font-medium text-red-700">오류:</h5>
                  {validationResult.errors.map((error, idx) => (
                    <div key={idx} className="p-2 bg-white rounded border border-red-200">
                      <p className="text-sm text-red-700">{error.message}</p>
                      {(error.line || error.column) && (
                        <p className="text-xs text-red-500 mt-1">
                          위치: {error.line && `Line ${error.line}`}{error.column && `, Column ${error.column}`}
                        </p>
                      )}
                      {error.suggestion && (
                        <p className="text-xs text-gray-600 mt-1 italic">
                          💡 {error.suggestion}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* 경고 목록 */}
              {validationResult.warnings.length > 0 && (
                <div className="space-y-1">
                  <h5 className="text-sm font-medium text-yellow-700">경고:</h5>
                  {validationResult.warnings.map((warning, idx) => (
                    <p key={idx} className="text-sm text-yellow-700 flex items-start gap-1">
                      <span>⚠️</span> {warning}
                    </p>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* 실제 테스트 결과 */}
          {testResult && (
            <div className={`p-4 rounded-lg border ${
              testResult.success
                ? 'bg-green-50 border-green-200'
                : 'bg-red-50 border-red-200'
            }`}>
              <div className="flex items-center gap-2 mb-3">
                {testResult.success ? (
                  <svg className="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                ) : (
                  <svg className="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                )}
                <h4 className={`font-medium ${testResult.success ? 'text-green-800' : 'text-red-800'}`}>
                  실제 테스트 {testResult.success ? '성공' : '실패'}
                </h4>
                <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">
                  {testResult.executionTimeMs}ms
                </span>
              </div>

              {testResult.message && (
                <p className={`text-sm ${testResult.success ? 'text-green-700' : 'text-gray-700'}`}>
                  {testResult.message}
                </p>
              )}

              {testResult.rowsAffected !== undefined && testResult.rowsAffected !== null && (
                <p className="text-sm text-gray-600 mt-1">
                  영향받은 행: {testResult.rowsAffected}
                </p>
              )}

              {/* 테스트 에러 */}
              {testResult.error && (
                <div className="mt-3 p-3 bg-white rounded border border-red-200">
                  <p className="text-sm font-medium text-red-700">{testResult.error.message}</p>
                  {testResult.error.code && (
                    <p className="text-xs text-red-500 mt-1">
                      에러 코드: {testResult.error.code}
                      {testResult.error.sqlState && ` (SQLState: ${testResult.error.sqlState})`}
                    </p>
                  )}
                  {testResult.error.suggestion && (
                    <p className="text-xs text-gray-600 mt-2 italic">
                      💡 {testResult.error.suggestion}
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Docker 안내 - 모바일에서 숨김 */}
          <div className="p-3 sm:p-4 bg-gray-50 rounded-lg border border-gray-200 hidden sm:block">
            <h4 className="text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
              <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              참고사항
            </h4>
            <ul className="text-xs text-gray-600 space-y-1">
              <li>• <strong>문법 검증</strong>은 JSQLParser를 사용하여 빠르게 SQL 문법을 검증합니다.</li>
              <li>• <strong>실제 테스트</strong>는 Docker가 필요하며, 첫 실행 시 컨테이너 시작에 시간이 걸릴 수 있습니다.</li>
              <li>• DryRun 모드에서는 트랜잭션이 롤백되어 실제 데이터에 영향을 주지 않습니다.</li>
              <li>• CREATE TABLE 등 DDL 문은 별도의 테이블 생성 없이 테스트됩니다.</li>
            </ul>
          </div>
        </div>

        {/* 푸터 */}
        <div className="px-4 sm:px-6 py-3 sm:py-4 bg-gray-50 border-t border-gray-200 flex justify-end">
          <button
            onClick={onClose}
            className="px-3 sm:px-4 py-2 text-xs sm:text-sm font-medium text-gray-700 bg-white border border-gray-300 hover:bg-gray-50 rounded-lg transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};