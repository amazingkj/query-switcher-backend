import React, { useRef, useState, useEffect } from 'react';
import Editor, { type Monaco, useMonaco } from '@monaco-editor/react';
import { editor, KeyMod, KeyCode } from 'monaco-editor';
import { formatSql, validateSql, minifySql } from '../utils/sqlFormatter';
import { registerSqlLanguage } from '../utils/sqlLanguageConfig';
import { DialectType } from '../types';
import { useTheme } from '../hooks/useTheme';

interface SqlEditorProps {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  placeholder?: string;
  height?: string;
  dialect?: DialectType;
  showToolbar?: boolean;
  onFormat?: () => void;
  onValidate?: () => void;
}

export const SqlEditor: React.FC<SqlEditorProps> = ({
  value,
  onChange,
  readOnly = false,
  placeholder: _placeholder = 'SQL 쿼리를 입력하세요...',
  height = '400px',
  dialect = DialectType.MYSQL,
  showToolbar = false,
  onFormat,
  onValidate
}) => {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const [isValid, setIsValid] = useState<boolean | null>(null);
  const { isDark } = useTheme();
  const monaco = useMonaco();

  // 현재 테마 이름
  const currentTheme = isDark ? 'vs-dark' : 'vs';

  // 다크모드 토글 시 Monaco Editor 테마 동적 업데이트
  useEffect(() => {
    if (monaco && editorRef.current) {
      monaco.editor.setTheme(currentTheme);
    }
  }, [isDark, monaco, currentTheme]);

  const handleEditorDidMount = (editorInstance: editor.IStandaloneCodeEditor, monaco: Monaco) => {
    editorRef.current = editorInstance;

    // 방언별 커스텀 SQL 언어 설정 등록
    registerSqlLanguage(monaco, dialect);

    // SQL 언어 설정
    monaco.editor.setModelLanguage(editorInstance.getModel()!, 'sql');

    // 포커스 설정
    if (!readOnly) {
      editorInstance.focus();
    }

    // 키보드 단축키 설정 - Format
    editorInstance.addCommand(KeyMod.CtrlCmd | KeyCode.KeyF, () => {
      handleFormat();
    });

    // 키보드 단축키 설정 - Validate (Alt+V로 변경, Ctrl+V는 붙여넣기용)
    editorInstance.addCommand(KeyMod.Alt | KeyCode.KeyV, () => {
      handleValidate();
    });
  };

  const handleEditorChange = (value: string | undefined) => {
    if (value !== undefined) {
      onChange(value);
      // 실시간 검증
      if (value.trim()) {
        const validation = validateSql(value);
        setIsValid(validation.isValid);
      } else {
        setIsValid(null);
      }
    }
  };

  const handleFormat = () => {
    if (editorRef.current && !readOnly) {
      const formatted = formatSql(value, dialect);
      editorRef.current.setValue(formatted);
      onChange(formatted);
    }
    onFormat?.();
  };

  const handleValidate = () => {
    const validation = validateSql(value);
    setIsValid(validation.isValid);
    onValidate?.();
  };

  const handleMinify = () => {
    if (editorRef.current && !readOnly) {
      const minified = minifySql(value);
      editorRef.current.setValue(minified);
      onChange(minified);
    }
  };

  const handleClear = () => {
    if (editorRef.current && !readOnly) {
      editorRef.current.setValue('');
      onChange('');
      setIsValid(null);
    }
  };

  return (
    <div className="sql-editor flex flex-col h-full">
      {showToolbar && !readOnly && (
        <div className="sql-editor-toolbar flex items-center justify-between p-2 bg-gray-100 dark:bg-gray-800 border-b border-gray-300 dark:border-gray-600 flex-shrink-0">
          <div className="flex items-center gap-2">
            <button
              onClick={handleFormat}
              className="px-3 py-1 text-sm bg-blue-600 text-white hover:bg-blue-700 focus:outline-none transition-all duration-200"
              title="포맷팅 (Ctrl+F)"
            >
              포맷
            </button>
            <button
              onClick={handleValidate}
              className="px-3 py-1 text-sm bg-green-600 text-white hover:bg-green-700 focus:outline-none transition-all duration-200"
              title="검증 (Alt+V)"
            >
              검증
            </button>
            <button
              onClick={handleMinify}
              className="px-3 py-1 text-sm bg-gray-600 text-white hover:bg-gray-700 focus:outline-none transition-all duration-200"
              title="압축"
            >
              압축
            </button>
            <button
              onClick={handleClear}
              className="px-3 py-1 text-sm bg-red-600 text-white hover:bg-red-700 focus:outline-none transition-all duration-200"
              title="지우기"
            >
              지우기
            </button>
          </div>
          <div className="flex items-center gap-2">
            {isValid !== null && (
              <span className={`text-sm px-2 py-1 ${
                isValid ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200' : 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200'
              }`}>
                {isValid ? '✓ 유효' : '✗ 오류'}
              </span>
            )}
            <span className="text-sm text-gray-500 dark:text-gray-400">
              {value.length.toLocaleString()}자
            </span>
          </div>
        </div>
      )}
      <div className="flex-1">
        <Editor
          height={showToolbar && !readOnly ? `calc(${height} - 50px)` : height}
          defaultLanguage="sql"
          value={value}
          onChange={handleEditorChange}
          onMount={handleEditorDidMount}
          theme={currentTheme}
          options={{
            readOnly,
            minimap: { enabled: false },
            wordWrap: 'on',
            lineNumbers: 'on',
            fontSize: 12,
            fontFamily: '"Monaco", "Menlo", "Ubuntu Mono", "Courier New", monospace',
            automaticLayout: true,
            scrollBeyondLastLine: false,
            renderWhitespace: 'selection',
            selectOnLineNumbers: true,
            roundedSelection: false,
            cursorStyle: 'line',
            contextmenu: true,
            mouseWheelZoom: true,
            smoothScrolling: true,
            overviewRulerLanes: 0,
            hideCursorInOverviewRuler: true,
            overviewRulerBorder: false,
            copyWithSyntaxHighlighting: true,
            emptySelectionClipboard: true,
            formatOnPaste: false,
            dragAndDrop: true,
            accessibilitySupport: 'on',
            suggest: {
              showKeywords: true,
              showSnippets: true,
              showFunctions: true,
              showFields: true
            },
            quickSuggestions: {
              other: true,
              comments: false,
              strings: false
            }
          }}
        />
      </div>
    </div>
  );
};
