import { create } from 'zustand';
import { DialectType, type ConversionResponse, type ConversionWarning } from '../types';

interface SqlState {
  // SQL 입력/출력
  inputSql: string;
  outputSql: string;
  
  // 데이터베이스 선택
  sourceDialect: DialectType;
  targetDialect: DialectType;
  
  // 변환 결과
  conversionResult: ConversionResponse | null;
  warnings: ConversionWarning[];
  
  // UI 상태
  isLoading: boolean;
  isAutoConvert: boolean;
  
  // 액션
  setInputSql: (sql: string) => void;
  setOutputSql: (sql: string) => void;
  setSourceDialect: (dialect: DialectType) => void;
  setTargetDialect: (dialect: DialectType) => void;
  setConversionResult: (result: ConversionResponse | null) => void;
  setWarnings: (warnings: ConversionWarning[]) => void;
  setLoading: (loading: boolean) => void;
  setAutoConvert: (auto: boolean) => void;
  clearResults: () => void;
}

export const useSqlStore = create<SqlState>((set) => ({
  // 초기 상태
  inputSql: '',
  outputSql: '',
  sourceDialect: DialectType.MYSQL,
  targetDialect: DialectType.POSTGRESQL,
  conversionResult: null,
  warnings: [],
  isLoading: false,
  isAutoConvert: false,

  // 액션들
  setInputSql: (sql: string) => set({ inputSql: sql }),
  setOutputSql: (sql: string) => set({ outputSql: sql }),
  setSourceDialect: (dialect: DialectType) => set({ sourceDialect: dialect }),
  setTargetDialect: (dialect: DialectType) => set({ targetDialect: dialect }),
  setConversionResult: (result: ConversionResponse | null) => set({ conversionResult: result }),
  setWarnings: (warnings: ConversionWarning[]) => set({ warnings }),
  setLoading: (loading: boolean) => set({ isLoading: loading }),
  setAutoConvert: (auto: boolean) => set({ isAutoConvert: auto }),
  
  clearResults: () => set({
    outputSql: '',
    conversionResult: null,
    warnings: []
  })
}));
