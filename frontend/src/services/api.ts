import axios, {type AxiosInstance, type AxiosResponse } from 'axios';
import type {ConversionRequest, ConversionResponse, ErrorResponse} from '../types';

// API 클라이언트 설정
const apiClient: AxiosInstance = axios.create({
  baseURL: 'http://localhost:18080/api/v1',
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

export default apiClient;
