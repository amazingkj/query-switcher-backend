import React, { useEffect, useState } from 'react';
import { DualSqlEditor } from './DualSqlEditor';
import { DatabaseSelector } from './DatabaseSelector';
import { WarningPanel } from './WarningPanel';
import { SqlSnippetPanel } from './SqlSnippetPanel';
import { ConversionHistoryPanel } from './ConversionHistoryPanel';
import { RealtimeSettingsPanel } from './RealtimeSettingsPanel';
import { ConversionGuidePanel } from './ConversionGuidePanel';
import { AnalyticsDashboard } from './AnalyticsDashboard';
import { SqlDiffViewer } from './SqlDiffViewer';
import { SqlValidationPanel } from './SqlValidationPanel';
import { useSqlStore } from '../stores/sqlStore';
import { useSqlConvert, useRealtimeConvert, useHealthCheck } from '../hooks/useSqlConvert';
import { useConversionHistory } from '../hooks/useConversionHistory';
import { useDebounce } from '../hooks/useDebounce';
import { useAnalytics, usePageTracking, useConversionTracking, useUserBehaviorTracking } from '../hooks/useAnalytics';
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

  // Analytics hooks
  usePageTracking('SQL Converter', '/converter');
  const { trackSqlConversion } = useConversionTracking();
  const { trackSqlInput, trackDialectChange, trackButtonClick, trackFeatureUse } = useUserBehaviorTracking();

  const [isSnippetPanelOpen, setIsSnippetPanelOpen] = useState(false);
  const [isHistoryPanelOpen, setIsHistoryPanelOpen] = useState(false);
  const [isSettingsPanelOpen, setIsSettingsPanelOpen] = useState(false);
  const [isGuidePanelOpen, setIsGuidePanelOpen] = useState(false);
  const [isAnalyticsDashboardOpen, setIsAnalyticsDashboardOpen] = useState(false);
  const [isValidationPanelOpen, setIsValidationPanelOpen] = useState(false);
  const [showDiffView, setShowDiffView] = useState(false);

  const convertMutation = useSqlConvert();
  const realtimeConvertMutation = useRealtimeConvert();
  const { data: healthData } = useHealthCheck();
  const { addConversion } = useConversionHistory();
  
  // 디바운스된 SQL 입력 (1초)
  const debouncedInputSql = useDebounce(inputSql, 1000);

  // SQL 입력 추적
  useEffect(() => {
    if (inputSql.trim()) {
      trackSqlInput(inputSql.length, sourceDialect);
    }
  }, [inputSql, sourceDialect, trackSqlInput]);

  // 방언 변경 추적
  useEffect(() => {
    trackDialectChange(sourceDialect, targetDialect);
  }, [sourceDialect, targetDialect, trackDialectChange]);

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
        
        // Analytics 추적
        trackSqlConversion(
          sourceDialect,
          targetDialect,
          inputSql.length,
          data.warnings.length > 0,
          data.warnings.length,
          data.conversionTime,
          data.success
        );
      },
      onError: (error) => {
        console.error('Conversion failed:', error);
        
        // 실패한 변환도 추적
        trackSqlConversion(
          sourceDialect,
          targetDialect,
          inputSql.length,
          false,
          0,
          0,
          false
        );
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
      trackButtonClick('copy_result', { 
        output_length: outputSql.length 
      });
      // toast.success('결과가 클립보드에 복사되었습니다.');
    }
  };

  const handleSwapDatabases = () => {
    const temp = sourceDialect;
    setSourceDialect(targetDialect);
    setTargetDialect(temp);
    clearResults();
    trackButtonClick('swap_databases', { 
      from: sourceDialect, 
      to: targetDialect 
    });
  };

  const handleSnippetSelect = (sql: string) => {
    setInputSql(sql);
    trackFeatureUse('sql_snippet', { 
      snippet_length: sql.length 
    });
  };

  const handleHistorySelect = (item: ConversionHistoryItem) => {
    setInputSql(item.originalSql);
    setSourceDialect(item.sourceDialect);
    setTargetDialect(item.targetDialect);
    setOutputSql(item.convertedSql);
    setWarnings(item.warnings);
    trackFeatureUse('conversion_history', { 
      history_timestamp: item.timestamp 
    });
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50">
      <div className="sql-converter max-w-7xl mx-auto p-4 sm:p-6 lg:p-8">
        {/* 헤더 */}
        <div className="mb-6 text-center sm:text-left">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-gray-900 mb-2">
                <span className="bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
                  SQL Query Switcher
                </span>
              </h1>
              <p className="text-gray-600 text-sm sm:text-base">
                데이터베이스 간 SQL 쿼리를 쉽게 변환하세요
              </p>
            </div>
            {healthData && (
              <div className="mt-4 sm:mt-0 flex items-center justify-center sm:justify-end">
                <div className="flex items-center px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  서버 연결됨
                </div>
              </div>
            )}
          </div>
        </div>

      {/* 데이터베이스 선택 */}
      <div className="mb-8">
        <DatabaseSelector
          sourceDialect={sourceDialect}
          targetDialect={targetDialect}
          onSourceChange={setSourceDialect}
          onTargetChange={setTargetDialect}
        />
        {/* 모바일: 그리드 배치, 데스크톱: flex-wrap */}
        <div className="mt-3 sm:mt-4 grid grid-cols-4 sm:flex sm:flex-wrap gap-1.5 sm:gap-2">
          <button
            onClick={handleSwapDatabases}
            className="flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:border-gray-400 hover:bg-gray-50 focus:outline-none focus:border-blue-500 focus:bg-gray-50 transition-all duration-200"
            title="방향 바꾸기"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
            </svg>
            <span className="hidden sm:inline">바꾸기</span>
          </button>
          <button
            onClick={() => {
              setIsSnippetPanelOpen(true);
              trackButtonClick('open_snippet_panel');
            }}
            className="flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-white bg-purple-600 rounded-lg hover:bg-purple-700 focus:outline-none focus:bg-purple-700 transition-all duration-200"
            title="SQL 스니펫"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
            </svg>
            <span className="hidden sm:inline">스니펫</span>
          </button>
          <button
            onClick={() => setIsHistoryPanelOpen(true)}
            className="flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 focus:outline-none focus:bg-indigo-700 transition-all duration-200"
            title="히스토리"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span className="hidden sm:inline">히스토리</span>
          </button>
          <button
            onClick={() => setIsSettingsPanelOpen(true)}
            className="flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-white bg-slate-600 rounded-lg hover:bg-slate-700 focus:outline-none focus:bg-slate-700 transition-all duration-200"
            title="설정"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <span className="hidden sm:inline">설정</span>
          </button>
          <button
            onClick={() => {
              setIsGuidePanelOpen(true);
              trackButtonClick('open_guide_panel');
            }}
            className="hidden sm:flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 focus:outline-none focus:bg-emerald-700 transition-all duration-200"
            title="변환 가이드"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span className="hidden sm:inline">가이드</span>
          </button>
          <button
            onClick={() => {
              setIsAnalyticsDashboardOpen(true);
              trackButtonClick('open_analytics_dashboard');
            }}
            className="hidden sm:flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-white bg-orange-600 rounded-lg hover:bg-orange-700 focus:outline-none focus:bg-orange-700 transition-all duration-200"
            title="분석"
          >
            <svg className="w-4 h-4 sm:w-3 sm:h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            <span className="hidden sm:inline">분석</span>
          </button>
          <label className="col-span-4 sm:col-span-1 flex items-center justify-center sm:justify-start px-3 py-2 sm:py-1.5 bg-gray-50 border border-gray-200 rounded-lg hover:border-gray-300 transition-all duration-200">
            <input
              type="checkbox"
              checked={isAutoConvert}
              onChange={(e) => setAutoConvert(e.target.checked)}
              className="w-3 h-3 text-blue-600 border border-gray-300 rounded focus:ring-1 focus:ring-blue-500 mr-2"
            />
            <span className="text-xs font-medium text-gray-700">자동 변환</span>
          </label>
        </div>
      </div>

      {/* SQL 에디터 영역 */}
      <div className="mb-6">
        {/* 모바일: 세로 배치, 데스크톱: 가로 배치 */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-3">
          <div className="flex items-center gap-2 sm:gap-3">
            <h3 className="text-sm sm:text-base font-semibold text-gray-800">SQL 변환기</h3>
            {/* 뷰 모드 토글 */}
            {outputSql && (
              <div className="flex bg-gray-200 rounded-lg p-0.5">
                <button
                  onClick={() => setShowDiffView(false)}
                  className={`px-2 sm:px-3 py-1 text-xs font-medium rounded-md transition-all ${
                    !showDiffView
                      ? 'bg-white text-gray-800 shadow-sm'
                      : 'text-gray-600 hover:text-gray-800'
                  }`}
                >
                  에디터
                </button>
                <button
                  onClick={() => {
                    setShowDiffView(true);
                    trackFeatureUse('diff_view');
                  }}
                  className={`px-2 sm:px-3 py-1 text-xs font-medium rounded-md transition-all ${
                    showDiffView
                      ? 'bg-white text-gray-800 shadow-sm'
                      : 'text-gray-600 hover:text-gray-800'
                  }`}
                >
                  <svg className="w-3 h-3 inline sm:mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                  <span className="hidden sm:inline">Diff</span>
                </button>
              </div>
            )}
          </div>
          <div className="flex gap-1.5 sm:gap-2">
            <button
              onClick={handleConvert}
              disabled={!inputSql.trim() || isLoading}
              className="flex-1 sm:flex-none px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-blue-700 transition-all duration-200"
            >
              <svg className="w-3 h-3 inline mr-1 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              {isLoading ? '변환중' : '변환'}
            </button>
            <button
              onClick={handleCopyResult}
              disabled={!outputSql}
              className="px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-emerald-700 transition-all duration-200"
              title="복사"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
              <span className="hidden sm:inline">복사</span>
            </button>
            <button
              onClick={() => {
                setIsValidationPanelOpen(true);
                trackFeatureUse('sql_validation');
              }}
              disabled={!outputSql}
              className="px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-teal-600 rounded-lg hover:bg-teal-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-teal-700 transition-all duration-200"
              title="검증"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span className="hidden sm:inline">검증</span>
            </button>
          </div>
        </div>

        {/* 에디터 또는 Diff 뷰어 - 모바일에서 높이 조정 */}
        {showDiffView && outputSql ? (
          <SqlDiffViewer
            originalSql={inputSql}
            convertedSql={outputSql}
            height="calc(100vh - 400px)"
            onClose={() => setShowDiffView(false)}
          />
        ) : (
          <DualSqlEditor
            inputValue={inputSql}
            outputValue={outputSql}
            onInputChange={setInputSql}
            inputPlaceholder="변환할 SQL 쿼리를 입력하세요..."
            outputPlaceholder="변환된 SQL이 여기에 표시됩니다..."
            height="calc(100vh - 400px)"
          />
        )}
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
      
      <AnalyticsDashboard
        isOpen={isAnalyticsDashboardOpen}
        onClose={() => setIsAnalyticsDashboardOpen(false)}
      />

      {/* SQL 검증 패널 */}
      <SqlValidationPanel
        sql={outputSql}
        dialect={targetDialect}
        isOpen={isValidationPanelOpen}
        onClose={() => setIsValidationPanelOpen(false)}
      />

      {/* Copyright Footer */}
      <div className="mt-12 pt-8 border-t border-gray-200">
        <p className="text-center text-xs text-gray-400">
          © 2025 SQL2SQL. All rights reserved.
        </p>
      </div>
      </div>
    </div>
  );
};
