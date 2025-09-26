import React, { useRef, useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { editor } from 'monaco-editor';
import { formatSql, validateSql, minifySql } from '../utils/sqlFormatter';
import { DialectType } from '../types';

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
  placeholder = 'SQL 쿼리를 입력하세요...',
  height = '400px',
  dialect = DialectType.MYSQL,
  showToolbar = false,
  onFormat,
  onValidate
}) => {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const [isValid, setIsValid] = useState<boolean | null>(null);

  const handleEditorDidMount = (editor: editor.IStandaloneCodeEditor) => {
    editorRef.current = editor;
    
    // SQL 언어 설정
    editor.getModel()?.setLanguage('sql');
    
    // 포커스 설정
    if (!readOnly) {
      editor.focus();
    }

    // 키보드 단축키 설정
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyF, () => {
      handleFormat();
    });

    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV, () => {
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
        <div className="sql-editor-toolbar flex items-center justify-between p-2 bg-gray-100 border-b border-gray-300 flex-shrink-0">
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
              title="검증 (Ctrl+V)"
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
                isValid ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
              }`}>
                {isValid ? '✓ 유효' : '✗ 오류'}
              </span>
            )}
            <span className="text-sm text-gray-500">
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
          options={{
            readOnly,
            minimap: { enabled: false },
            wordWrap: 'on',
            lineNumbers: 'on',
            fontSize: 14,
            fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
            theme: 'vs-light',
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
