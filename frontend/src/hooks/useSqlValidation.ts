import { useMutation, useQuery } from '@tanstack/react-query';
import { sqlValidationApi } from '../services/api';
import type { ValidationRequest, ValidationResponse, TestRequest, TestResponse } from '../types';
import toast from 'react-hot-toast';

// SQL 문법 검증 mutation
export const useSqlValidate = () => {
  return useMutation({
    mutationFn: (request: ValidationRequest) => sqlValidationApi.validateSyntax(request),
    onMutate: () => {
      toast.loading('SQL 문법 검증 중...', { id: 'validating' });
    },
    onSuccess: (data: ValidationResponse) => {
      if (data.isValid) {
        toast.success('문법 검증 통과!', { id: 'validating' });
      } else {
        toast.error('문법 오류 발견', { id: 'validating' });
      }
    },
    onError: (error: any) => {
      console.error('Validation error:', error);
      toast.error('검증 요청 실패', { id: 'validating' });
    }
  });
};

// SQL 실제 테스트 mutation
export const useSqlTest = () => {
  return useMutation({
    mutationFn: (request: TestRequest) => sqlValidationApi.testSql(request),
    onMutate: () => {
      toast.loading('SQL 테스트 실행 중... (Docker 컨테이너 시작 중)', { id: 'testing' });
    },
    onSuccess: (data: TestResponse) => {
      if (data.success) {
        toast.success(`테스트 성공! (${data.executionTimeMs}ms)`, { id: 'testing' });
      } else {
        toast.error('테스트 실패', { id: 'testing' });
      }
    },
    onError: (error: any) => {
      console.error('Test error:', error);
      toast.error('테스트 요청 실패. Docker가 실행 중인지 확인하세요.', { id: 'testing' });
    }
  });
};

// 컨테이너 상태 쿼리
export const useContainerStatus = () => {
  return useQuery({
    queryKey: ['containerStatus'],
    queryFn: sqlValidationApi.getContainerStatus,
    refetchInterval: 10000, // 10초마다 체크
    retry: 1
  });
};