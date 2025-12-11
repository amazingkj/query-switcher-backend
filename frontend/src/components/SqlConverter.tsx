import React, { useEffect, useState, useRef } from 'react';
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
import { ThemeToggle } from './ThemeToggle';
import { SqlExecutionPanel } from './SqlExecutionPanel';
import { DbStatusIndicator } from './DbStatusIndicator';
import { useSqlStore } from '../stores/sqlStore';
import { useSqlConvert, useRealtimeConvert, useHealthCheck } from '../hooks/useSqlConvert';
import { useConversionHistory } from '../hooks/useConversionHistory';
import { useDebounce } from '../hooks/useDebounce';
import { usePageTracking, useConversionTracking, useUserBehaviorTracking } from '../hooks/useAnalytics';
import type {ConversionRequest, ConversionHistoryItem} from '../types';
import toast from 'react-hot-toast';

// SQL 길이 제한 상수
const SQL_WARNING_LENGTH = 50000;  // 50,000자 이상 경고
const SQL_MAX_LENGTH = 500000;     // 500,000자 하드 리밋

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
  const [showExecutionPanel, setShowExecutionPanel] = useState(false);

  // 파일 업로드 ref
  const fileInputRef = useRef<HTMLInputElement>(null);

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

    // SQL 길이 검증
    if (inputSql.length > SQL_MAX_LENGTH) {
      toast.error(`SQL이 너무 깁니다. 최대 ${SQL_MAX_LENGTH.toLocaleString()}자까지 허용됩니다.`);
      return;
    }

    // 긴 SQL 경고 (차단하지 않음)
    if (inputSql.length > SQL_WARNING_LENGTH) {
      toast('쿼리가 깁니다. 변환 시간이 오래 걸릴 수 있습니다.', {
        icon: '⚠️',
        duration: 3000
      });
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

  // SQL 파일 업로드 핸들러
  const handleFileUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // 파일 확장자 검증
    if (!file.name.toLowerCase().endsWith('.sql')) {
      toast.error('.sql 파일만 업로드할 수 있습니다.');
      return;
    }

    // 파일 크기 검증 (5MB 제한)
    if (file.size > 5 * 1024 * 1024) {
      toast.error('파일이 너무 큽니다. 최대 5MB까지 허용됩니다.');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      if (content.length > SQL_MAX_LENGTH) {
        toast.error(`SQL이 너무 깁니다. 최대 ${SQL_MAX_LENGTH.toLocaleString()}자까지 허용됩니다.`);
        return;
      }
      setInputSql(content);
      toast.success(`${file.name} 파일이 로드되었습니다.`);
      trackFeatureUse('file_upload', { file_size: file.size });
    };
    reader.onerror = () => {
      toast.error('파일을 읽는 중 오류가 발생했습니다.');
    };
    reader.readAsText(file);

    // input 초기화 (같은 파일 재선택 가능하도록)
    event.target.value = '';
  };

  // SQL 파일 다운로드 핸들러
  const handleDownload = () => {
    if (!outputSql) return;

    const blob = new Blob([outputSql], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    // 파일명: converted_{target}_{timestamp}.sql
    const timestamp = new Date().toISOString().slice(0, 19).replace(/[:-]/g, '');
    link.href = url;
    link.download = `converted_${targetDialect.toLowerCase()}_${timestamp}.sql`;

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    toast.success('파일이 다운로드되었습니다.');
    trackFeatureUse('file_download', { output_length: outputSql.length });
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 dark:from-gray-900 dark:via-gray-900 dark:to-gray-800 transition-colors duration-300">
      <div className="sql-converter max-w-7xl mx-auto p-4 sm:p-6 lg:p-8">
        {/* 헤더 */}
        <div className="mb-6 text-center sm:text-left">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-gray-900 dark:text-white mb-2">
                <span className="bg-gradient-to-r from-blue-600 to-purple-600 dark:from-blue-400 dark:to-purple-400 bg-clip-text text-transparent">
                  SQL Query Switcher
                </span>
              </h1>
              <p className="text-gray-600 dark:text-gray-400 text-sm sm:text-base">
                데이터베이스 간 SQL 쿼리를 쉽게 변환하세요
              </p>
            </div>
            <div className="mt-4 sm:mt-0 flex items-center justify-center sm:justify-end gap-3">
              <ThemeToggle />
              {healthData && (
                <div className="flex items-center px-3 py-1 bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-400 rounded-full text-sm">
                  <div className="w-2 h-2 bg-green-500 rounded-full mr-2 animate-pulse"></div>
                  서버 연결됨
                </div>
              )}
            </div>
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
            className="flex items-center justify-center p-2 sm:px-4 sm:py-1.5 text-xs font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg hover:border-gray-400 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:border-blue-500 focus:bg-gray-50 dark:focus:bg-gray-700 transition-all duration-200"
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
          <label className="col-span-4 sm:col-span-1 flex items-center justify-center sm:justify-start px-3 py-2 sm:py-1.5 bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-lg hover:border-gray-300 dark:hover:border-gray-500 transition-all duration-200">
            <input
              type="checkbox"
              checked={isAutoConvert}
              onChange={(e) => setAutoConvert(e.target.checked)}
              className="w-3 h-3 text-blue-600 border border-gray-300 dark:border-gray-500 rounded focus:ring-1 focus:ring-blue-500 mr-2"
            />
            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">자동 변환</span>
          </label>
        </div>
      </div>

      {/* SQL 에디터 영역 */}
      <div className="mb-6">
        {/* 모바일: 세로 배치, 데스크톱: 가로 배치 */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-3">
          <div className="flex items-center gap-2 sm:gap-3">
            <h3 className="text-sm sm:text-base font-semibold text-gray-800 dark:text-gray-200">SQL 변환기</h3>
            {/* 뷰 모드 토글 */}
            {outputSql && (
              <div className="flex bg-gray-200 dark:bg-gray-700 rounded-lg p-0.5">
                <button
                  onClick={() => setShowDiffView(false)}
                  className={`px-2 sm:px-3 py-1 text-xs font-medium rounded-md transition-all ${
                    !showDiffView
                      ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm'
                      : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
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
                      ? 'bg-white dark:bg-gray-600 text-gray-800 dark:text-white shadow-sm'
                      : 'text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
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
            {/* 파일 업로드 (숨겨진 input) */}
            <input
              ref={fileInputRef}
              type="file"
              accept=".sql"
              onChange={handleFileUpload}
              className="hidden"
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg hover:border-gray-400 dark:hover:border-gray-500 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:border-blue-500 transition-all duration-200"
              title="SQL 파일 업로드"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
              <span className="hidden sm:inline">업로드</span>
            </button>
            <button
              onClick={handleConvert}
              disabled={!inputSql.trim() || isLoading}
              className="flex items-center flex-1 sm:flex-none px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-blue-700 transition-all duration-200"
            >
              <svg className="w-3 h-3 mr-1 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              {isLoading ? '변환중' : '변환'}
            </button>
            <button
              onClick={handleCopyResult}
              disabled={!outputSql}
              className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-emerald-700 transition-all duration-200"
              title="복사"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
              <span className="hidden sm:inline">복사</span>
            </button>
            <button
              onClick={handleDownload}
              disabled={!outputSql}
              className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-violet-600 rounded-lg hover:bg-violet-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-violet-700 transition-all duration-200"
              title="다운로드"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              <span className="hidden sm:inline">다운로드</span>
            </button>
            <button
              onClick={() => {
                setIsValidationPanelOpen(true);
                trackFeatureUse('sql_validation');
              }}
              disabled={!outputSql}
              className="flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white bg-teal-600 rounded-lg hover:bg-teal-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:bg-teal-700 transition-all duration-200"
              title="검증"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span className="hidden sm:inline">검증</span>
            </button>
            <button
              onClick={() => {
                setShowExecutionPanel(!showExecutionPanel);
                trackFeatureUse('db_test');
              }}
              disabled={!outputSql}
              className={`flex items-center px-3 sm:px-4 py-2 sm:py-1.5 text-xs font-medium text-white rounded-lg transition-all duration-200 ${
                showExecutionPanel
                  ? 'bg-cyan-700 ring-2 ring-cyan-400'
                  : 'bg-cyan-600 hover:bg-cyan-700'
              } disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none`}
              title="DB 테스트"
            >
              <svg className="w-3 h-3 sm:mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01" />
              </svg>
              <span className="hidden sm:inline">DB테스트</span>
            </button>
          </div>
          {/* DB 상태 인디케이터 */}
          <div className="hidden sm:block">
            <DbStatusIndicator compact />
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

      {/* SQL 실행 테스트 패널 */}
      {showExecutionPanel && outputSql && (
        <div className="mb-6">
          <SqlExecutionPanel
            sql={outputSql}
            dialect={targetDialect}
            onClose={() => setShowExecutionPanel(false)}
          />
        </div>
      )}

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
      <div className="mt-12 pt-8 border-t border-gray-200 dark:border-gray-700">
        <p className="text-center text-xs text-gray-400 dark:text-gray-500">
          © 2025 SQL2SQL. All rights reserved.
        </p>
      </div>
      </div>
    </div>
  );
};
