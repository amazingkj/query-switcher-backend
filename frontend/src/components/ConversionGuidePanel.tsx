import React, { useState } from 'react';
import { DialectType } from '../types';

interface ConversionGuidePanelProps {
  isOpen: boolean;
  onClose: () => void;
  sourceDialect: DialectType;
  targetDialect: DialectType;
}

// 데이터베이스별 지원 기능
const DATABASE_FEATURES = {
  [DialectType.MYSQL]: {
    name: 'MySQL',
    color: 'bg-orange-500',
    features: [
      'LIMIT/OFFSET',
      'DATE_FORMAT',
      'IFNULL',
      'CONCAT',
      'GROUP_CONCAT',
      'AUTO_INCREMENT',
      'ENGINE 옵션'
    ],
    limitations: [
      'CTE (Common Table Expression)',
      'WINDOW 함수',
      'ARRAY 타입',
      'JSON 함수 (8.0 이전)'
    ]
  },
  [DialectType.POSTGRESQL]: {
    name: 'PostgreSQL',
    color: 'bg-blue-600',
    features: [
      'LIMIT/OFFSET',
      'TO_CHAR',
      'COALESCE',
      'ARRAY 타입',
      'JSON 함수',
      'WINDOW 함수',
      'CTE',
      'ILIKE'
    ],
    limitations: [
      'AUTO_INCREMENT',
      'ENGINE 옵션',
      'GROUP_CONCAT',
      'DATE_FORMAT'
    ]
  },
  [DialectType.ORACLE]: {
    name: 'Oracle',
    color: 'bg-red-600',
    features: [
      'ROWNUM',
      'TO_CHAR',
      'NVL',
      'CONNECT BY',
      'DUAL 테이블',
      'SEQUENCE',
      'PARTITION'
    ],
    limitations: [
      'LIMIT/OFFSET',
      'AUTO_INCREMENT',
      'BOOLEAN 타입',
      'JSON 함수'
    ]
  }
};

// 변환 가이드 타입
interface ConversionIssue {
  issue: string;
  mysql?: string;
  postgresql?: string;
  oracle?: string;
  note: string;
}

interface ConversionGuide {
  title: string;
  commonIssues: ConversionIssue[];
}

// 변환 가이드
const CONVERSION_GUIDES: Record<string, ConversionGuide> = {
  [`${DialectType.MYSQL}_${DialectType.POSTGRESQL}`]: {
    title: 'MySQL → PostgreSQL 변환 가이드',
    commonIssues: [
      {
        issue: 'LIMIT 구문',
        mysql: 'SELECT * FROM table LIMIT 10 OFFSET 20;',
        postgresql: 'SELECT * FROM table LIMIT 10 OFFSET 20;',
        note: 'PostgreSQL도 동일한 구문을 지원합니다.'
      },
      {
        issue: '날짜 포맷팅',
        mysql: "SELECT DATE_FORMAT(date_col, '%Y-%m-%d') FROM table;",
        postgresql: "SELECT TO_CHAR(date_col, 'YYYY-MM-DD') FROM table;",
        note: 'DATE_FORMAT → TO_CHAR로 변경하고 포맷 문자열을 조정해야 합니다.'
      },
      {
        issue: 'NULL 처리',
        mysql: 'SELECT IFNULL(col, 0) FROM table;',
        postgresql: 'SELECT COALESCE(col, 0) FROM table;',
        note: 'IFNULL → COALESCE로 변경합니다.'
      }
    ]
  },
  [`${DialectType.MYSQL}_${DialectType.ORACLE}`]: {
    title: 'MySQL → Oracle 변환 가이드',
    commonIssues: [
      {
        issue: 'LIMIT 구문',
        mysql: 'SELECT * FROM table LIMIT 10 OFFSET 20;',
        oracle: 'SELECT * FROM (SELECT a.*, ROWNUM rn FROM table a WHERE ROWNUM <= 30) WHERE rn > 20;',
        note: 'Oracle은 ROWNUM을 사용하며 서브쿼리가 필요합니다.'
      },
      {
        issue: '날짜 포맷팅',
        mysql: "SELECT DATE_FORMAT(date_col, '%Y-%m-%d') FROM table;",
        oracle: "SELECT TO_CHAR(date_col, 'YYYY-MM-DD') FROM table;",
        note: 'DATE_FORMAT → TO_CHAR로 변경합니다.'
      },
      {
        issue: 'NULL 처리',
        mysql: 'SELECT IFNULL(col, 0) FROM table;',
        oracle: 'SELECT NVL(col, 0) FROM table;',
        note: 'IFNULL → NVL로 변경합니다.'
      }
    ]
  }
};

export const ConversionGuidePanel: React.FC<ConversionGuidePanelProps> = ({
  isOpen,
  onClose,
  sourceDialect,
  targetDialect
}) => {
  const [activeTab, setActiveTab] = useState<'features' | 'guide' | 'tips'>('features');

  if (!isOpen) return null;

  const sourceFeatures = DATABASE_FEATURES[sourceDialect];
  const targetFeatures = DATABASE_FEATURES[targetDialect];
  const conversionGuide = CONVERSION_GUIDES[`${sourceDialect}_${targetDialect}`];

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-800">
            {sourceFeatures.name} → {targetFeatures.name} 변환 가이드
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 탭 네비게이션 */}
        <div className="flex border-b border-gray-200">
          <button
            onClick={() => setActiveTab('features')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'features'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            데이터베이스 특징
          </button>
          <button
            onClick={() => setActiveTab('guide')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'guide'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            변환 가이드
          </button>
          <button
            onClick={() => setActiveTab('tips')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'tips'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            변환 팁
          </button>
        </div>

        {/* 탭 내용 */}
        <div className="flex-1 overflow-y-auto p-4">
          {activeTab === 'features' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* 소스 데이터베이스 */}
              <div>
                <h3 className="text-lg font-semibold text-gray-800 mb-3">
                  {sourceFeatures.name} 특징
                </h3>
                <div className="space-y-4">
                  <div>
                    <h4 className="font-medium text-green-600 mb-2">✅ 지원 기능</h4>
                    <ul className="space-y-1">
                      {sourceFeatures.features.map((feature, index) => (
                        <li key={index} className="text-sm text-gray-600 flex items-center">
                          <span className="w-2 h-2 bg-green-500 rounded-full mr-2"></span>
                          {feature}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4 className="font-medium text-red-600 mb-2">❌ 제한사항</h4>
                    <ul className="space-y-1">
                      {sourceFeatures.limitations.map((limitation, index) => (
                        <li key={index} className="text-sm text-gray-600 flex items-center">
                          <span className="w-2 h-2 bg-red-500 rounded-full mr-2"></span>
                          {limitation}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              {/* 타겟 데이터베이스 */}
              <div>
                <h3 className="text-lg font-semibold text-gray-800 mb-3">
                  {targetFeatures.name} 특징
                </h3>
                <div className="space-y-4">
                  <div>
                    <h4 className="font-medium text-green-600 mb-2">✅ 지원 기능</h4>
                    <ul className="space-y-1">
                      {targetFeatures.features.map((feature, index) => (
                        <li key={index} className="text-sm text-gray-600 flex items-center">
                          <span className="w-2 h-2 bg-green-500 rounded-full mr-2"></span>
                          {feature}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4 className="font-medium text-red-600 mb-2">❌ 제한사항</h4>
                    <ul className="space-y-1">
                      {targetFeatures.limitations.map((limitation, index) => (
                        <li key={index} className="text-sm text-gray-600 flex items-center">
                          <span className="w-2 h-2 bg-red-500 rounded-full mr-2"></span>
                          {limitation}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'guide' && conversionGuide && (
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">
                {conversionGuide.title}
              </h3>
              <div className="space-y-4">
                {conversionGuide.commonIssues.map((issue: ConversionIssue, index: number) => {
                  const sourceCode = sourceDialect === DialectType.MYSQL ? issue.mysql
                    : sourceDialect === DialectType.POSTGRESQL ? issue.postgresql
                    : issue.oracle;
                  const targetCode = targetDialect === DialectType.MYSQL ? issue.mysql
                    : targetDialect === DialectType.POSTGRESQL ? issue.postgresql
                    : issue.oracle;

                  return (
                    <div key={index} className="border border-gray-200 rounded-lg p-4">
                      <h4 className="font-medium text-gray-800 mb-3">{issue.issue}</h4>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                        <div>
                          <h5 className="text-sm font-medium text-red-600 mb-2">원본 ({sourceFeatures.name})</h5>
                          <pre className="text-xs bg-red-50 p-2 rounded border overflow-x-auto">
                            <code>{sourceCode}</code>
                          </pre>
                        </div>
                        <div>
                          <h5 className="text-sm font-medium text-green-600 mb-2">변환 ({targetFeatures.name})</h5>
                          <pre className="text-xs bg-green-50 p-2 rounded border overflow-x-auto">
                            <code>{targetCode}</code>
                          </pre>
                        </div>
                      </div>
                      <div className="text-sm text-gray-600 bg-blue-50 p-2 rounded">
                        <strong>💡 참고:</strong> {issue.note}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {activeTab === 'tips' && (
            <div>
              <h3 className="text-lg font-semibold text-gray-800 mb-4">변환 팁</h3>
              <div className="space-y-4">
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                  <h4 className="font-medium text-blue-800 mb-2">🔍 변환 전 체크리스트</h4>
                  <ul className="text-sm text-blue-700 space-y-1">
                    <li>• 데이터 타입 호환성 확인</li>
                    <li>• 함수명 및 문법 차이점 파악</li>
                    <li>• 제약조건 및 인덱스 고려</li>
                    <li>• 성능에 영향을 줄 수 있는 구문 식별</li>
                  </ul>
                </div>
                
                <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                  <h4 className="font-medium text-green-800 mb-2">✅ 변환 후 검증</h4>
                  <ul className="text-sm text-green-700 space-y-1">
                    <li>• 변환된 SQL 문법 검증</li>
                    <li>• 데이터 타입 정확성 확인</li>
                    <li>• 성능 테스트 수행</li>
                    <li>• 예상 결과와 실제 결과 비교</li>
                  </ul>
                </div>
                
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                  <h4 className="font-medium text-yellow-800 mb-2">⚠️ 주의사항</h4>
                  <ul className="text-sm text-yellow-700 space-y-1">
                    <li>• 자동 변환으로 해결되지 않는 부분은 수동 검토 필요</li>
                    <li>• 데이터베이스별 최적화 기법 고려</li>
                    <li>• 트랜잭션 및 동시성 처리 방식 차이</li>
                    <li>• 에러 처리 및 예외 상황 대응</li>
                  </ul>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
