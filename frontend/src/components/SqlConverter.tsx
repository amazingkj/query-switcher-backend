import React, { useEffect, useState } from 'react';
import { DualSqlEditor } from './DualSqlEditor';
import { DatabaseSelector } from './DatabaseSelector';
import { WarningPanel } from './WarningPanel';
import { ErrorDisplay } from './ErrorDisplay';
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
import { useSqlConverterHandlers } from '../hooks/useSqlConverterHandlers';
import { usePageTracking, useUserBehaviorTracking } from '../hooks/useAnalytics';

export const SqlConverter: React.FC = () => {
  const {
    inputSql,
    outputSql,
    sourceDialect,
    targetDialect,
    warnings,
    error,
    clearError,
    isLoading,
    setInputSql,
    setSourceDialect,
    setTargetDialect
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
    handleCopyResult,
    handleSwapDatabases,
    handleSnippetSelect,
    handleHistorySelect,
    handleFileUpload,
    handleDownload,
    triggerFileUpload
  } = useSqlConverterHandlers();

  const { data: healthData } = useHealthCheck();

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

        {/* 에러 표시 */}
        <ErrorDisplay
          error={error}
          onDismiss={clearError}
          showDetails={true}
        />

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
          <div className="flex flex-col items-center gap-2">
            <div className="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
              <a
                href="https://github.com/amazingkj/query-switcher-backend"
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                  <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.604-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.464-1.11-1.464-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z"/>
                </svg>
                GitHub
              </a>
              <span className="text-gray-300 dark:text-gray-600">|</span>
              <a
                href="https://github.com/amazingkj/query-switcher-backend/issues"
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                Issue / Feedback
              </a>
            </div>
            <p className="text-center text-xs text-gray-400 dark:text-gray-500">
              © 2025 SQL2SQL. All rights reserved.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};