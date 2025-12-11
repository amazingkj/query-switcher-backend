import axios, {type AxiosInstance, type AxiosResponse } from 'axios';
import type {
  ConversionRequest,
  ConversionResponse,
  ErrorResponse,
  ValidationRequest,
  ValidationResponse,
  TestRequest,
  TestResponse,
  ContainerStatusResponse,
  ExecuteRequest,
  ExecutionResult,
  ConnectionStatus,
  DialectType
} from '../types';

// API 클라이언트 설정
const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터
apiClient.interceptors.request.use(
  (config) => {
    console.log(`API Request: ${config.method?.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => {
    console.error('Request Error:', error);
    return Promise.reject(error);
  }
);

// 응답 인터셉터
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    console.log(`API Response: ${response.status} ${response.config.url}`);
    return response;
  },
  (error) => {
    console.error('Response Error:', error);
    
    // 에러 응답 처리
    if (error.response) {
      const errorData: ErrorResponse = error.response.data;
      console.error('Error Details:', errorData);
    }
    
    return Promise.reject(error);
  }
);

// SQL 변환 API
export const sqlConverterApi = {
  // SQL 변환
  convertSql: async (request: ConversionRequest): Promise<ConversionResponse> => {
    const response = await apiClient.post<ConversionResponse>('/convert', request);
    return response.data;
  },

  // 헬스 체크
  healthCheck: async (): Promise<{ status: string; service: string }> => {
    const response = await apiClient.get('/health');
    return response.data;
  }
};

// SQL 검증 API
export const sqlValidationApi = {
  // SQL 문법 검증 (빠름)
  validateSyntax: async (request: ValidationRequest): Promise<ValidationResponse> => {
    const response = await apiClient.post<ValidationResponse>('/validate/syntax', request);
    return response.data;
  },

  // SQL 실제 테스트 (Testcontainers)
  testSql: async (request: TestRequest): Promise<TestResponse> => {
    // 테스트는 시간이 걸릴 수 있으므로 타임아웃 증가
    const response = await apiClient.post<TestResponse>('/validate/test', request, {
      timeout: 120000 // 2분
    });
    return response.data;
  },

  // 컨테이너 상태 확인
  getContainerStatus: async (): Promise<ContainerStatusResponse> => {
    const response = await apiClient.get<ContainerStatusResponse>('/validate/containers/status');
    return response.data;
  }
};

// SQL 실행 API (Docker DB 테스트용)
export const sqlExecutionApi = {
  // SQL 실행
  execute: async (request: ExecuteRequest): Promise<ExecutionResult> => {
    const response = await apiClient.post<ExecutionResult>('/execute', request, {
      timeout: 30000 // 30초
    });
    return response.data;
  },

  // 특정 DB 연결 상태 확인
  checkConnection: async (dialect: DialectType): Promise<ConnectionStatus> => {
    const response = await apiClient.get<ConnectionStatus>(`/execute/status/${dialect}`);
    return response.data;
  },

  // 모든 DB 연결 상태 확인
  checkAllConnections: async (): Promise<Record<DialectType, ConnectionStatus>> => {
    const response = await apiClient.get<Record<DialectType, ConnectionStatus>>('/execute/status');
    return response.data;
  }
};

export default apiClient;
