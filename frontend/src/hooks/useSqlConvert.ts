import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { sqlConverterApi } from '../services/api';
import type {ConversionRequest, ConversionResponse} from '../types';
import { useSqlStore } from '../stores/sqlStore';
import toast from 'react-hot-toast';

// SQL 변환 mutation
export const useSqlConvert = () => {
  const { setConversionResult, setWarnings, setOutputSql, setLoading } = useSqlStore();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: ConversionRequest) => sqlConverterApi.convertSql(request),
    onMutate: () => {
      setLoading(true);
      toast.loading('SQL 변환 중...', { id: 'converting' });
    },
    onSuccess: (data: ConversionResponse) => {
      setConversionResult(data);
      setWarnings(data.warnings);
      setOutputSql(data.convertedSql);
      
      // 변환 결과를 캐시에 저장
      queryClient.setQueryData(['conversion', data.originalSql, data.sourceDialect, data.targetDialect], data);
      
      if (data.success) {
        toast.success('SQL 변환 완료!', { id: 'converting' });
      } else {
        toast.error('변환 중 오류가 발생했습니다.', { id: 'converting' });
      }
    },
    onError: (error: any) => {
      console.error('Conversion error:', error);
      toast.error('변환 요청 실패', { id: 'converting' });
    },
    onSettled: () => {
      setLoading(false);
    }
  });
};

// 실시간 변환을 위한 훅
export const useRealtimeConvert = () => {
  const { setOutputSql, setWarnings, setLoading } = useSqlStore();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: ConversionRequest) => sqlConverterApi.convertSql(request),
    onMutate: () => {
      setLoading(true);
    },
    onSuccess: (data: ConversionResponse) => {
      setOutputSql(data.convertedSql);
      setWarnings(data.warnings);
      
      // 캐시에 저장
      queryClient.setQueryData(['conversion', data.originalSql, data.sourceDialect, data.targetDialect], data);
    },
    onError: (error: any) => {
      console.error('Realtime conversion error:', error);
      setOutputSql('');
      setWarnings([]);
    },
    onSettled: () => {
      setLoading(false);
    }
  });
};

// 헬스 체크 쿼리
export const useHealthCheck = () => {
  return useQuery({
    queryKey: ['health'],
    queryFn: sqlConverterApi.healthCheck,
    refetchInterval: 30000, // 30초마다 체크
    retry: 3
  });
};
