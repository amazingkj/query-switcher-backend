import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { sqlConverterApi } from '../services/api';
import type { ConversionRequest, ConversionResponse } from '../types';
import { useSqlStore } from '../stores/sqlStore';
import { formatSql } from '../utils/sqlFormatter';
import toast from 'react-hot-toast';

interface ConvertOptions {
  /** true면 toast 알림을 표시하지 않음 */
  silent?: boolean;
  /** true면 전체 변환 결과를 저장 (히스토리용) */
  saveFullResult?: boolean;
}

/**
 * SQL 변환 훅
 * @param options.silent - true면 toast 알림 없이 조용히 변환
 * @param options.saveFullResult - true면 setConversionResult 호출
 */
export const useSqlConvert = (options: ConvertOptions = {}) => {
  const { silent = false, saveFullResult = true } = options;
  const { setConversionResult, setWarnings, setOutputSql, setLoading, isPrettyFormat } = useSqlStore();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: ConversionRequest) => sqlConverterApi.convertSql(request),
    onMutate: () => {
      setLoading(true);
      if (!silent) {
        toast.loading('SQL 변환 중...', { id: 'converting' });
      }
    },
    onSuccess: (data: ConversionResponse) => {
      if (saveFullResult) {
        setConversionResult(data);
      }
      setWarnings(data.warnings);

      // 포맷팅 설정에 따라 SQL 포맷팅 적용
      const outputSql = isPrettyFormat
        ? formatSql(data.convertedSql, data.targetDialect)
        : data.convertedSql;
      setOutputSql(outputSql);

      // 변환 결과를 캐시에 저장
      queryClient.setQueryData(
        ['conversion', data.originalSql, data.sourceDialect, data.targetDialect],
        data
      );

      if (!silent) {
        if (data.success) {
          toast.success('SQL 변환 완료!', { id: 'converting' });
        } else {
          toast.error('변환 중 오류가 발생했습니다.', { id: 'converting' });
        }
      }
    },
    onError: (error: Error) => {
      console.error('Conversion error:', error);
      if (silent) {
        setOutputSql('');
        setWarnings([]);
      } else {
        toast.error('변환 요청 실패', { id: 'converting' });
      }
    },
    onSettled: () => {
      setLoading(false);
    }
  });
};

/**
 * 실시간 변환을 위한 훅 (silent 모드의 useSqlConvert)
 * @deprecated useSqlConvert({ silent: true, saveFullResult: false }) 사용 권장
 */
export const useRealtimeConvert = () => {
  return useSqlConvert({ silent: true, saveFullResult: false });
};

/**
 * 헬스 체크 쿼리
 */
export const useHealthCheck = () => {
  return useQuery({
    queryKey: ['health'],
    queryFn: sqlConverterApi.healthCheck,
    refetchInterval: 30000, // 30초마다 체크
    retry: 3
  });
};