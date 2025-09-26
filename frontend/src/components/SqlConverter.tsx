import React, { useEffect, useState } from 'react';
import { DualSqlEditor } from './DualSqlEditor';
import { DatabaseSelector } from './DatabaseSelector';
import { WarningPanel } from './WarningPanel';
import { SqlSnippetPanel } from './SqlSnippetPanel';
import { ConversionHistoryPanel } from './ConversionHistoryPanel';
import { RealtimeSettingsPanel } from './RealtimeSettingsPanel';
import { ConversionGuidePanel } from './ConversionGuidePanel';
import { useSqlStore } from '../stores/sqlStore';
import { useSqlConvert, useRealtimeConvert, useHealthCheck } from '../hooks/useSqlConvert';
import { useConversionHistory } from '../hooks/useConversionHistory';
import { useDebounce } from '../hooks/useDebounce';
import type {ConversionRequest, ConversionHistoryItem} from '../types';

export const SqlConverter: React.FC = () => {
  const {
    inputSql,
    outputSql,
    sourceDialect,
    targetDialect,
    warnings,
    isLoading,
    isAutoConvert,
    setInputSql,
    setOutputSql,
    setSourceDialect,
    setTargetDialect,
    setWarnings,
    setAutoConvert,
    clearResults
  } = useSqlStore();

  const [isSnippetPanelOpen, setIsSnippetPanelOpen] = useState(false);
  const [isHistoryPanelOpen, setIsHistoryPanelOpen] = useState(false);
  const [isSettingsPanelOpen, setIsSettingsPanelOpen] = useState(false);
  const [isGuidePanelOpen, setIsGuidePanelOpen] = useState(false);

  const convertMutation = useSqlConvert();
  const realtimeConvertMutation = useRealtimeConvert();
  const { data: healthData } = useHealthCheck();
  const { addConversion } = useConversionHistory();
  
  // 디바운스된 SQL 입력 (1초)
  const debouncedInputSql = useDebounce(inputSql, 1000);

  // 자동 변환 로직
  useEffect(() => {
    if (isAutoConvert && debouncedInputSql.trim() && debouncedInputSql !== inputSql) {
      handleRealtimeConvert();
    }
  }, [debouncedInputSql, isAutoConvert]);

  const handleConvert = () => {
    if (!inputSql.trim()) {
      return;
    }

    const request: ConversionRequest = {
      sql: inputSql,
      sourceDialect,
      targetDialect,
      options: {
        strictMode: false,
        enableComments: true,
        formatSql: true,
        replaceUnsupportedFunctions: false
      }
    };

    convertMutation.mutate(request, {
      onSuccess: (data) => {
        // 히스토리에 추가
        addConversion(data);
      }
    });
  };

  const handleRealtimeConvert = () => {
    if (!inputSql.trim()) {
      return;
    }

    const request: ConversionRequest = {
      sql: inputSql,
      sourceDialect,
      targetDialect,
      options: {
        strictMode: false,
        enableComments: true,
        formatSql: true,
        replaceUnsupportedFunctions: false
      }
    };

    realtimeConvertMutation.mutate(request, {
      onSuccess: (data) => {
        // 히스토리에 추가
        addConversion(data);
      }
    });
  };

  const handleCopyResult = () => {
    if (outputSql) {
      navigator.clipboard.writeText(outputSql);
      // toast.success('결과가 클립보드에 복사되었습니다.');
    }
  };

  const handleSwapDatabases = () => {
    const temp = sourceDialect;
    setSourceDialect(targetDialect);
    setTargetDialect(temp);
    clearResults();
  };

  const handleSnippetSelect = (sql: string) => {
    setInputSql(sql);
  };

  const handleHistorySelect = (item: ConversionHistoryItem) => {
    setInputSql(item.originalSql);
    setSourceDialect(item.sourceDialect);
    setTargetDialect(item.targetDialect);
    setOutputSql(item.convertedSql);
    setWarnings(item.warnings);
  };

  return (
    <div className="sql-converter max-w-7xl mx-auto p-8">
      {/* 헤더 */}
      <div className="mb-8">
        <h1 className="text-4xl font-bold text-gray-900 mb-3">
          SQL Query Switcher
        </h1>
        <p className="text-lg text-gray-600">
          데이터베이스 간 SQL 쿼리를 쉽게 변환하세요
        </p>
        {healthData && (
          <div className="mt-3 inline-flex items-center px-3 py-1 bg-green-50 border border-green-200 text-sm text-green-700 font-medium">
            <div className="w-2 h-2 bg-green-500 rounded-full mr-2"></div>
            서버 상태: {healthData.status}
          </div>
        )}
      </div>

      {/* 데이터베이스 선택 */}
      <div className="mb-8">
        <DatabaseSelector
          sourceDialect={sourceDialect}
          targetDialect={targetDialect}
          onSourceChange={setSourceDialect}
          onTargetChange={setTargetDialect}
        />
        <div className="mt-6 flex flex-wrap gap-3">
          <button
            onClick={handleSwapDatabases}
            className="px-6 py-2.5 text-sm font-medium text-gray-700 bg-white border-2 border-gray-300 hover:border-gray-400 hover:bg-gray-50 focus:outline-none focus:border-blue-500 focus:bg-gray-50 transition-all duration-200"
          >
            <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
            </svg>
            방향 바꾸기
          </button>
          <button
            onClick={() => setIsSnippetPanelOpen(true)}
            className="px-6 py-2.5 text-sm font-medium text-white bg-purple-600 hover:bg-purple-700 focus:outline-none focus:bg-purple-700 transition-all duration-200"
          >
            <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
            </svg>
            SQL 스니펫
          </button>
          <button
            onClick={() => setIsHistoryPanelOpen(true)}
            className="px-6 py-2.5 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:bg-indigo-700 transition-all duration-200"
          >
            <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            히스토리
          </button>
          <button
            onClick={() => setIsSettingsPanelOpen(true)}
            className="px-6 py-2.5 text-sm font-medium text-white bg-slate-600 hover:bg-slate-700 focus:outline-none focus:bg-slate-700 transition-all duration-200"
          >
            <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            설정
          </button>
          <button
            onClick={() => setIsGuidePanelOpen(true)}
            className="px-6 py-2.5 text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:bg-emerald-700 transition-all duration-200"
          >
            <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            변환 가이드
          </button>
          <label className="flex items-center px-4 py-2.5 bg-gray-50 border-2 border-gray-200 hover:border-gray-300 transition-all duration-200">
            <input
              type="checkbox"
              checked={isAutoConvert}
              onChange={(e) => setAutoConvert(e.target.checked)}
              className="w-4 h-4 text-blue-600 border-2 border-gray-300 focus:ring-2 focus:ring-blue-500 mr-3"
            />
            <span className="text-sm font-medium text-gray-700">자동 변환</span>
          </label>
        </div>
      </div>

      {/* SQL 에디터 영역 */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-800">SQL 변환기</h3>
          <div className="flex gap-3">
            <button
              onClick={handleConvert}
              disabled={!inputSql.trim() || isLoading}
              className="px-6 py-2.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-blue-700 transition-all duration-200"
            >
              <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              {isLoading ? '변환 중...' : '변환하기'}
            </button>
            <button
              onClick={handleCopyResult}
              disabled={!outputSql}
              className="px-6 py-2.5 text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-emerald-700 transition-all duration-200"
            >
              <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
              복사
            </button>
          </div>
        </div>
        
        <DualSqlEditor
          inputValue={inputSql}
          outputValue={outputSql}
          onInputChange={setInputSql}
          inputPlaceholder="변환할 SQL 쿼리를 입력하세요..."
          outputPlaceholder="변환된 SQL이 여기에 표시됩니다..."
          height="650px"
        />
      </div>

      {/* 경고 패널 */}
      <WarningPanel 
        warnings={warnings} 
        sourceDialect={sourceDialect}
        targetDialect={targetDialect}
      />

      {/* SQL 스니펫 패널 */}
      <SqlSnippetPanel
        isOpen={isSnippetPanelOpen}
        onClose={() => setIsSnippetPanelOpen(false)}
        onSnippetSelect={handleSnippetSelect}
        dialect={sourceDialect}
      />

      {/* 변환 히스토리 패널 */}
      <ConversionHistoryPanel
        isOpen={isHistoryPanelOpen}
        onClose={() => setIsHistoryPanelOpen(false)}
        onSelectHistory={handleHistorySelect}
      />

      {/* 실시간 설정 패널 */}
      <RealtimeSettingsPanel
        isOpen={isSettingsPanelOpen}
        onClose={() => setIsSettingsPanelOpen(false)}
      />

      {/* 변환 가이드 패널 */}
      <ConversionGuidePanel
        isOpen={isGuidePanelOpen}
        onClose={() => setIsGuidePanelOpen(false)}
        sourceDialect={sourceDialect}
        targetDialect={targetDialect}
      />
    </div>
  );
};
