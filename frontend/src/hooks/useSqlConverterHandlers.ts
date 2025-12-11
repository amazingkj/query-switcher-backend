import { useRef, useCallback } from 'react';
import { useSqlStore } from '../stores/sqlStore';
import { useSqlConvert, useRealtimeConvert } from './useSqlConvert';
import { useConversionHistory } from './useConversionHistory';
import { useConversionTracking, useUserBehaviorTracking } from './useAnalytics';
import { SQL_LIMITS, DEFAULT_CONVERSION_OPTIONS } from '../config/sqlLimits';
import type { ConversionRequest, ConversionHistoryItem } from '../types';
import toast from 'react-hot-toast';

/**
 * SQL 변환기의 핸들러 로직을 담당하는 커스텀 훅
 */
export function useSqlConverterHandlers() {
  const {
    inputSql,
    outputSql,
    sourceDialect,
    targetDialect,
    setInputSql,
    setOutputSql,
    setSourceDialect,
    setTargetDialect,
    setWarnings,
    clearResults
  } = useSqlStore();

  const fileInputRef = useRef<HTMLInputElement>(null);

  const convertMutation = useSqlConvert();
  const realtimeConvertMutation = useRealtimeConvert();
  const { addConversion } = useConversionHistory();
  const { trackSqlConversion } = useConversionTracking();
  const { trackButtonClick, trackFeatureUse } = useUserBehaviorTracking();

  /**
   * SQL 변환 요청 생성
   */
  const createConversionRequest = useCallback((): ConversionRequest => ({
    sql: inputSql,
    sourceDialect,
    targetDialect,
    options: DEFAULT_CONVERSION_OPTIONS
  }), [inputSql, sourceDialect, targetDialect]);

  /**
   * 메인 변환 핸들러
   */
  const handleConvert = useCallback(() => {
    if (!inputSql.trim()) return;

    // SQL 길이 검증
    if (inputSql.length > SQL_LIMITS.MAX_LENGTH) {
      toast.error(`SQL이 너무 깁니다. 최대 ${SQL_LIMITS.MAX_LENGTH.toLocaleString()}자까지 허용됩니다.`);
      return;
    }

    // 긴 SQL 경고 (차단하지 않음)
    if (inputSql.length > SQL_LIMITS.WARNING_LENGTH) {
      toast('쿼리가 깁니다. 변환 시간이 오래 걸릴 수 있습니다.', {
        icon: '⚠️',
        duration: 3000
      });
    }

    const request = createConversionRequest();

    convertMutation.mutate(request, {
      onSuccess: (data) => {
        addConversion(data);
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
  }, [inputSql, sourceDialect, targetDialect, createConversionRequest, convertMutation, addConversion, trackSqlConversion]);

  /**
   * 실시간 변환 핸들러
   */
  const handleRealtimeConvert = useCallback(() => {
    if (!inputSql.trim()) return;

    const request = createConversionRequest();
    realtimeConvertMutation.mutate(request, {
      onSuccess: (data) => {
        addConversion(data);
      }
    });
  }, [inputSql, createConversionRequest, realtimeConvertMutation, addConversion]);

  /**
   * 결과 복사 핸들러
   */
  const handleCopyResult = useCallback(() => {
    if (outputSql) {
      navigator.clipboard.writeText(outputSql);
      trackButtonClick('copy_result', { output_length: outputSql.length });
    }
  }, [outputSql, trackButtonClick]);

  /**
   * DB 방향 스왑 핸들러
   */
  const handleSwapDatabases = useCallback(() => {
    const temp = sourceDialect;
    setSourceDialect(targetDialect);
    setTargetDialect(temp);
    clearResults();
    trackButtonClick('swap_databases', { from: sourceDialect, to: targetDialect });
  }, [sourceDialect, targetDialect, setSourceDialect, setTargetDialect, clearResults, trackButtonClick]);

  /**
   * 스니펫 선택 핸들러
   */
  const handleSnippetSelect = useCallback((sql: string) => {
    setInputSql(sql);
    trackFeatureUse('sql_snippet', { snippet_length: sql.length });
  }, [setInputSql, trackFeatureUse]);

  /**
   * 히스토리 선택 핸들러
   */
  const handleHistorySelect = useCallback((item: ConversionHistoryItem) => {
    setInputSql(item.originalSql);
    setSourceDialect(item.sourceDialect);
    setTargetDialect(item.targetDialect);
    setOutputSql(item.convertedSql);
    setWarnings(item.warnings);
    trackFeatureUse('conversion_history', { history_timestamp: item.timestamp });
  }, [setInputSql, setSourceDialect, setTargetDialect, setOutputSql, setWarnings, trackFeatureUse]);

  /**
   * 파일 업로드 핸들러
   */
  const handleFileUpload = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // 파일 확장자 검증
    if (!file.name.toLowerCase().endsWith('.sql')) {
      toast.error('.sql 파일만 업로드할 수 있습니다.');
      return;
    }

    // 파일 크기 검증
    if (file.size > SQL_LIMITS.FILE_MAX_SIZE) {
      toast.error('파일이 너무 큽니다. 최대 5MB까지 허용됩니다.');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      if (content.length > SQL_LIMITS.MAX_LENGTH) {
        toast.error(`SQL이 너무 깁니다. 최대 ${SQL_LIMITS.MAX_LENGTH.toLocaleString()}자까지 허용됩니다.`);
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
  }, [setInputSql, trackFeatureUse]);

  /**
   * 파일 다운로드 핸들러
   */
  const handleDownload = useCallback(() => {
    if (!outputSql) return;

    const blob = new Blob([outputSql], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    const timestamp = new Date().toISOString().slice(0, 19).replace(/[:-]/g, '');
    link.href = url;
    link.download = `converted_${targetDialect.toLowerCase()}_${timestamp}.sql`;

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    toast.success('파일이 다운로드되었습니다.');
    trackFeatureUse('file_download', { output_length: outputSql.length });
  }, [outputSql, targetDialect, trackFeatureUse]);

  /**
   * 파일 업로드 트리거
   */
  const triggerFileUpload = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  return {
    fileInputRef,
    handleConvert,
    handleRealtimeConvert,
    handleCopyResult,
    handleSwapDatabases,
    handleSnippetSelect,
    handleHistorySelect,
    handleFileUpload,
    handleDownload,
    triggerFileUpload,
    isConverting: convertMutation.isPending || realtimeConvertMutation.isPending
  };
}