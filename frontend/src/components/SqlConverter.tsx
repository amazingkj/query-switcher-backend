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
import { SqlExecutionPanel } from './SqlExecutionPanel';
import { ConverterHeader } from './ConverterHeader';
import { ConverterToolbar } from './ConverterToolbar';
import { EditorActionBar } from './EditorActionBar';
import { useSqlStore } from '../stores/sqlStore';
import { useHealthCheck } from '../hooks/useSqlConvert';
import { useDebounce } from '../hooks/useDebounce';
import { useSqlConverterHandlers } from '../hooks/useSqlConverterHandlers';
import { usePageTracking, useUserBehaviorTracking } from '../hooks/useAnalytics';

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
    setSourceDialect,
    setTargetDialect,
    setAutoConvert
  } = useSqlStore();

  // Analytics hooks
  usePageTracking('SQL Converter', '/converter');
  const { trackSqlInput, trackDialectChange } = useUserBehaviorTracking();

  // Panel states
  const [isSnippetPanelOpen, setIsSnippetPanelOpen] = useState(false);
  const [isHistoryPanelOpen, setIsHistoryPanelOpen] = useState(false);
  const [isSettingsPanelOpen, setIsSettingsPanelOpen] = useState(false);
  const [isGuidePanelOpen, setIsGuidePanelOpen] = useState(false);
  const [isAnalyticsDashboardOpen, setIsAnalyticsDashboardOpen] = useState(false);
  const [isValidationPanelOpen, setIsValidationPanelOpen] = useState(false);
  const [showDiffView, setShowDiffView] = useState(false);
  const [showExecutionPanel, setShowExecutionPanel] = useState(false);

  // Handlers hook
  const {
    fileInputRef,
    handleConvert,
    handleRealtimeConvert,
    handleCopyResult,
    handleSwapDatabases,
    handleSnippetSelect,
    handleHistorySelect,
    handleFileUpload,
    handleDownload,
    triggerFileUpload
  } = useSqlConverterHandlers();

  const { data: healthData } = useHealthCheck();
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
  }, [debouncedInputSql, isAutoConvert, inputSql, handleRealtimeConvert]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 dark:from-gray-900 dark:via-gray-900 dark:to-gray-800 transition-colors duration-300">
      <div className="sql-converter max-w-7xl mx-auto p-4 sm:p-6 lg:p-8">
        {/* 헤더 */}
        <ConverterHeader isServerConnected={!!healthData} />

        {/* 데이터베이스 선택 */}
        <div className="mb-8">
          <DatabaseSelector
            sourceDialect={sourceDialect}
            targetDialect={targetDialect}
            onSourceChange={setSourceDialect}
            onTargetChange={setTargetDialect}
          />
          <ConverterToolbar
            isAutoConvert={isAutoConvert}
            onAutoConvertChange={setAutoConvert}
            onSwapDatabases={handleSwapDatabases}
            onOpenSnippetPanel={() => setIsSnippetPanelOpen(true)}
            onOpenHistoryPanel={() => setIsHistoryPanelOpen(true)}
            onOpenSettingsPanel={() => setIsSettingsPanelOpen(true)}
            onOpenGuidePanel={() => setIsGuidePanelOpen(true)}
            onOpenAnalyticsPanel={() => setIsAnalyticsDashboardOpen(true)}
          />
        </div>

        {/* SQL 에디터 영역 */}
        <div className="mb-6">
          <EditorActionBar
            inputSql={inputSql}
            outputSql={outputSql}
            isLoading={isLoading}
            showDiffView={showDiffView}
            showExecutionPanel={showExecutionPanel}
            onConvert={handleConvert}
            onCopyResult={handleCopyResult}
            onDownload={handleDownload}
            onTriggerFileUpload={triggerFileUpload}
            onSetShowDiffView={setShowDiffView}
            onSetShowExecutionPanel={setShowExecutionPanel}
            onOpenValidationPanel={() => setIsValidationPanelOpen(true)}
            fileInputRef={fileInputRef}
            onFileUpload={handleFileUpload}
          />

          {/* 에디터 또는 Diff 뷰어 */}
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

        {/* 패널들 */}
        <SqlSnippetPanel
          isOpen={isSnippetPanelOpen}
          onClose={() => setIsSnippetPanelOpen(false)}
          onSnippetSelect={handleSnippetSelect}
          dialect={sourceDialect}
        />

        <ConversionHistoryPanel
          isOpen={isHistoryPanelOpen}
          onClose={() => setIsHistoryPanelOpen(false)}
          onSelectHistory={handleHistorySelect}
        />

        <RealtimeSettingsPanel
          isOpen={isSettingsPanelOpen}
          onClose={() => setIsSettingsPanelOpen(false)}
        />

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