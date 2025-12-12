import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { sqlExecutionApi } from '../services/api';
import type { DialectType, ExecutionResult } from '../types';
import toast from 'react-hot-toast';

interface SqlExecutionPanelProps {
  sql: string;
  dialect: DialectType;
  onClose?: () => void;
}

export const SqlExecutionPanel: React.FC<SqlExecutionPanelProps> = ({
  sql,
  dialect,
  onClose
}) => {
  const [dryRun, setDryRun] = useState(true);
  const [result, setResult] = useState<ExecutionResult | null>(null);

  // DB 연결 상태 확인
  const { data: connectionStatus, isLoading: isCheckingConnection } = useQuery({
    queryKey: ['dbConnection', dialect],
    queryFn: () => sqlExecutionApi.checkConnection(dialect),
    refetchInterval: 10000, // 10초마다 재확인
  });

  // SQL 실행 mutation
  const executeMutation = useMutation({
    mutationFn: () => sqlExecutionApi.execute({ sql, dialect, dryRun }),
    onSuccess: (data) => {
      setResult(data);
      if (data.success) {
        toast.success(`실행 완료 (${data.executionTimeMs}ms)`);
      } else {
        toast.error('실행 실패');
      }
    },
    onError: (error: any) => {
      toast.error(error.message || '실행 중 오류 발생');
    }
  });

  const handleExecute = () => {
    if (!sql.trim()) {
      toast.error('실행할 SQL이 없습니다.');
      return;
    }
    executeMutation.mutate();
  };

  const getDialectName = (d: DialectType) => {
    switch (d) {
      case 'MYSQL': return 'MySQL';
      case 'POSTGRESQL': return 'PostgreSQL';
      case 'ORACLE': return 'Oracle';
      default: return d;
    }
  };

  const getDialectColor = (d: DialectType) => {
    switch (d) {
      case 'MYSQL': return 'bg-orange-500';
      case 'POSTGRESQL': return 'bg-blue-600';
      case 'ORACLE': return 'bg-red-600';
      default: return 'bg-gray-500';
    }
  };

  return (
    <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg">
      {/* 헤더 */}
      <div className="flex items-center justify-between p-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <div className={`w-3 h-3 rounded-full ${getDialectColor(dialect)}`}></div>
          <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200">
            {getDialectName(dialect)} DB 테스트
          </h3>
          {/* 연결 상태 */}
          {isCheckingConnection ? (
            <span className="text-xs text-gray-500 dark:text-gray-400">확인 중...</span>
          ) : connectionStatus?.connected ? (
            <span className="flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
              <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
              연결됨 {connectionStatus.version && `(${connectionStatus.version.split(' ')[0]})`}
            </span>
          ) : (
            <span className="flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
              <span className="w-2 h-2 bg-red-500 rounded-full"></span>
              연결 안됨
            </span>
          )}
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>

      {/* 컨트롤 */}
      <div className="p-3 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            {/* DryRun 토글 */}
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={dryRun}
                onChange={(e) => setDryRun(e.target.checked)}
                className="w-4 h-4 text-blue-600 rounded focus:ring-blue-500"
              />
              <span className="text-sm text-gray-700 dark:text-gray-300">
                DryRun (롤백)
              </span>
            </label>
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {dryRun ? '변경사항이 롤백됩니다' : '⚠️ 실제 DB에 반영됩니다'}
            </span>
          </div>

          <button
            onClick={handleExecute}
            disabled={executeMutation.isPending || !connectionStatus?.connected}
            className={`px-4 py-1.5 text-sm font-medium text-white rounded transition-colors ${
              executeMutation.isPending || !connectionStatus?.connected
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-green-600 hover:bg-green-700'
            }`}
          >
            {executeMutation.isPending ? (
              <span className="flex items-center gap-2">
                <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                실행 중...
              </span>
            ) : (
              '실행'
            )}
          </button>
        </div>
      </div>

      {/* SQL 미리보기 */}
      <div className="p-3 border-b border-gray-200 dark:border-gray-700">
        <h4 className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">실행할 SQL</h4>
        <pre className="text-xs bg-gray-50 dark:bg-gray-900 p-2 rounded border border-gray-200 dark:border-gray-700 overflow-x-auto max-h-32 text-gray-800 dark:text-gray-200">
          <code>{sql || '(SQL 없음)'}</code>
        </pre>
      </div>

      {/* 결과 */}
      {result && (
        <div className="p-3">
          <h4 className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">실행 결과</h4>

          {result.success ? (
            <div className="space-y-3">
              {/* 성공 메시지 */}
              <div className="flex items-center gap-2 text-green-600 dark:text-green-400">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                <span className="text-sm font-medium">
                  {result.message} ({result.executionTimeMs}ms)
                </span>
              </div>

              {/* 영향받은 행 수 */}
              {result.rowsAffected !== undefined && (
                <p className="text-sm text-gray-600 dark:text-gray-400">
                  {result.data ? `${result.rowsAffected}행 조회됨` : `${result.rowsAffected}행 영향받음`}
                </p>
              )}

              {/* SELECT 결과 테이블 */}
              {result.data && result.data.length > 0 && result.columns && (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-xs border border-gray-200 dark:border-gray-700">
                    <thead className="bg-gray-100 dark:bg-gray-700">
                      <tr>
                        {result.columns.map((col, idx) => (
                          <th key={idx} className="px-3 py-2 text-left font-medium text-gray-700 dark:text-gray-300 border-b border-gray-200 dark:border-gray-600">
                            {col.name}
                            <span className="ml-1 text-gray-400 dark:text-gray-500 font-normal">({col.type})</span>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {result.data.slice(0, 100).map((row, rowIdx) => (
                        <tr key={rowIdx} className={rowIdx % 2 === 0 ? 'bg-white dark:bg-gray-800' : 'bg-gray-50 dark:bg-gray-750'}>
                          {result.columns!.map((col, colIdx) => (
                            <td key={colIdx} className="px-3 py-2 text-gray-800 dark:text-gray-200 border-b border-gray-200 dark:border-gray-700 whitespace-nowrap">
                              {row[col.name] === null ? (
                                <span className="text-gray-400 italic">NULL</span>
                              ) : (
                                String(row[col.name])
                              )}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {result.data.length > 100 && (
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                      ... 외 {result.data.length - 100}행 더 있음 (최대 100행 표시)
                    </p>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="space-y-2">
              {/* 실패 메시지 */}
              <div className="flex items-start gap-2 text-red-600 dark:text-red-400">
                <svg className="w-5 h-5 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
                <div>
                  <p className="text-sm font-medium">실행 실패</p>
                  {result.error && (
                    <div className="mt-1 text-xs">
                      <p className="text-red-500 dark:text-red-400">{result.error.message}</p>
                      {result.error.code && (
                        <p className="text-gray-500 dark:text-gray-400">에러 코드: {result.error.code}</p>
                      )}
                      {result.error.suggestion && (
                        <p className="mt-1 text-blue-600 dark:text-blue-400">
                          💡 {result.error.suggestion}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 연결 안됨 안내 */}
      {!connectionStatus?.connected && !isCheckingConnection && (
        <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 border-t border-yellow-200 dark:border-yellow-800">
          <p className="text-xs text-yellow-800 dark:text-yellow-400">
            ⚠️ {getDialectName(dialect)} 데이터베이스에 연결할 수 없습니다.
          </p>
          <p className="text-xs text-yellow-700 dark:text-yellow-500 mt-1">
            Docker 컨테이너를 시작하세요: <code className="bg-yellow-100 dark:bg-yellow-900 px-1 rounded">docker-compose -f docker-compose.test.yml up -d</code>
          </p>
        </div>
      )}
    </div>
  );
};