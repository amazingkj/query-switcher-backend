import React, { useState, useRef, useEffect } from 'react';
import { SqlEditor } from './SqlEditor';

interface DualSqlEditorProps {
  inputValue: string;
  outputValue: string;
  onInputChange: (value: string) => void;
  inputPlaceholder?: string;
  outputPlaceholder?: string;
  height?: string;
}

export const DualSqlEditor: React.FC<DualSqlEditorProps> = ({
  inputValue,
  outputValue,
  onInputChange,
  inputPlaceholder = '변환할 SQL 쿼리를 입력하세요...',
  outputPlaceholder = '변환된 SQL이 여기에 표시됩니다...',
  height = '500px'
}) => {
  const [leftWidth, setLeftWidth] = useState(50); // 퍼센트
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const handleMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleMouseMove = (e: MouseEvent) => {
    if (!isDragging || !containerRef.current) return;

    const containerRect = containerRef.current.getBoundingClientRect();
    const newLeftWidth = ((e.clientX - containerRect.left) / containerRect.width) * 100;
    
    // 최소/최대 너비 제한 (20% ~ 80%)
    const clampedWidth = Math.min(Math.max(newLeftWidth, 20), 80);
    setLeftWidth(clampedWidth);
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  useEffect(() => {
    if (isDragging) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging]);

  return (
    <div
      ref={containerRef}
      className="dual-sql-editor flex border-2 border-gray-300 overflow-hidden"
      style={{ height }}
    >
      {/* 왼쪽 에디터 (입력) */}
      <div 
        className="flex-shrink-0 border-r border-gray-300"
        style={{ width: `${leftWidth}%` }}
      >
        <SqlEditor
          value={inputValue}
          onChange={onInputChange}
          placeholder={inputPlaceholder}
          height="100%"
        />
      </div>

      {/* 리사이즈 핸들 */}
      <div
        className="w-1 bg-gray-300 hover:bg-blue-500 cursor-col-resize flex-shrink-0 transition-colors"
        onMouseDown={handleMouseDown}
      >
        <div className="w-full h-full flex items-center justify-center">
          <div className="w-0.5 h-8 bg-gray-400 rounded-full"></div>
        </div>
      </div>

      {/* 오른쪽 에디터 (출력) */}
      <div 
        className="flex-1"
        style={{ width: `${100 - leftWidth}%` }}
      >
        <SqlEditor
          value={outputValue}
          onChange={() => {}} // 읽기 전용
          readOnly
          placeholder={outputPlaceholder}
          height="100%"
        />
      </div>
    </div>
  );
};
