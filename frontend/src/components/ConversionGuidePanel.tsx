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
    version: '8.0+',
    description: '가장 널리 사용되는 오픈소스 RDBMS. 웹 애플리케이션에 최적화.',
    features: [
      'LIMIT/OFFSET 페이징',
      'DATE_FORMAT 날짜 포맷팅',
      'IFNULL NULL 처리',
      'CONCAT 문자열 연결',
      'GROUP_CONCAT 그룹 연결',
      'AUTO_INCREMENT 자동 증가',
      'JSON 타입 및 함수 (8.0+)',
      'CTE WITH 구문 (8.0+)',
      'WINDOW 함수 (8.0+)'
    ],
    limitations: [
      'ARRAY 타입 미지원',
      'MERGE 문 미지원',
      '|| 문자열 연결 (기본 모드)',
      'FULL OUTER JOIN 미지원'
    ]
  },
  [DialectType.POSTGRESQL]: {
    name: 'PostgreSQL',
    color: 'bg-blue-600',
    version: '14+',
    description: '강력한 기능과 확장성을 갖춘 오픈소스 객체-관계형 데이터베이스.',
    features: [
      'LIMIT/OFFSET 페이징',
      'TO_CHAR 날짜/숫자 포맷팅',
      'COALESCE NULL 처리',
      '|| 문자열 연결',
      'ARRAY 타입 지원',
      'JSON/JSONB 타입 및 함수',
      'WINDOW 함수',
      'CTE WITH 구문',
      'ILIKE 대소문자 무시 검색',
      'RETURNING 절',
      'SERIAL/IDENTITY 자동 증가'
    ],
    limitations: [
      'AUTO_INCREMENT 키워드',
      'DATE_FORMAT 함수',
      'IFNULL 함수',
      'GROUP_CONCAT 함수'
    ]
  },
  [DialectType.ORACLE]: {
    name: 'Oracle',
    color: 'bg-red-600',
    version: '19c+',
    description: '엔터프라이즈급 상용 RDBMS. 대규모 트랜잭션 처리에 최적화.',
    features: [
      'ROWNUM 행 번호',
      'TO_CHAR 날짜/숫자 포맷팅',
      'NVL/NVL2 NULL 처리',
      'DECODE 조건 분기',
      '|| 문자열 연결',
      'CONNECT BY 계층 쿼리',
      'DUAL 테이블',
      'SEQUENCE 시퀀스',
      'PARTITION 파티셔닝',
      'MERGE 문',
      'ROWID 행 식별자',
      'FETCH FIRST (12c+)'
    ],
    limitations: [
      'LIMIT/OFFSET 구문',
      'AUTO_INCREMENT 키워드',
      'BOOLEAN 타입 (PL/SQL만)',
      'ILIKE 구문'
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
  // MySQL → PostgreSQL
  [`${DialectType.MYSQL}_${DialectType.POSTGRESQL}`]: {
    title: 'MySQL → PostgreSQL 변환 가이드',
    commonIssues: [
      {
        issue: 'LIMIT 구문',
        mysql: 'SELECT * FROM users LIMIT 10 OFFSET 20;',
        postgresql: 'SELECT * FROM users LIMIT 10 OFFSET 20;',
        note: 'PostgreSQL도 동일한 구문을 지원합니다.'
      },
      {
        issue: '날짜 포맷팅',
        mysql: "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')",
        postgresql: "TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS')",
        note: 'DATE_FORMAT → TO_CHAR로 변경하고, 포맷 문자열을 Oracle 스타일로 변환합니다.'
      },
      {
        issue: 'NULL 처리',
        mysql: 'SELECT IFNULL(email, "없음") FROM users;',
        postgresql: "SELECT COALESCE(email, '없음') FROM users;",
        note: 'IFNULL → COALESCE로 변경합니다. COALESCE는 여러 값을 받을 수 있습니다.'
      },
      {
        issue: '문자열 연결',
        mysql: "CONCAT(first_name, ' ', last_name)",
        postgresql: "first_name || ' ' || last_name",
        note: 'CONCAT 함수 대신 || 연산자를 사용하거나, CONCAT도 지원합니다.'
      },
      {
        issue: 'AUTO_INCREMENT',
        mysql: 'id INT AUTO_INCREMENT PRIMARY KEY',
        postgresql: 'id SERIAL PRIMARY KEY',
        note: 'AUTO_INCREMENT → SERIAL 또는 GENERATED AS IDENTITY로 변경합니다.'
      }
    ]
  },
  // MySQL → Oracle
  [`${DialectType.MYSQL}_${DialectType.ORACLE}`]: {
    title: 'MySQL → Oracle 변환 가이드',
    commonIssues: [
      {
        issue: 'LIMIT 구문',
        mysql: 'SELECT * FROM users LIMIT 10;',
        oracle: 'SELECT * FROM users WHERE ROWNUM <= 10;',
        note: 'Oracle 12c 이전: ROWNUM 사용, 12c 이후: FETCH FIRST 10 ROWS ONLY 사용 가능.'
      },
      {
        issue: '날짜 포맷팅',
        mysql: "DATE_FORMAT(created_at, '%Y-%m-%d')",
        oracle: "TO_CHAR(created_at, 'YYYY-MM-DD')",
        note: 'DATE_FORMAT → TO_CHAR로 변경합니다.'
      },
      {
        issue: 'NULL 처리',
        mysql: 'SELECT IFNULL(email, "없음") FROM users;',
        oracle: "SELECT NVL(email, '없음') FROM users;",
        note: 'IFNULL → NVL로 변경합니다. NVL2(expr, val_if_not_null, val_if_null)도 사용 가능.'
      },
      {
        issue: '문자열 연결',
        mysql: "CONCAT(first_name, ' ', last_name)",
        oracle: "first_name || ' ' || last_name",
        note: 'CONCAT은 Oracle에서 2개 인자만 받으므로 || 연산자를 권장합니다.'
      },
      {
        issue: '현재 날짜/시간',
        mysql: 'NOW(), CURDATE()',
        oracle: 'SYSDATE, CURRENT_TIMESTAMP',
        note: 'NOW() → SYSDATE, CURDATE() → TRUNC(SYSDATE)로 변경합니다.'
      }
    ]
  },
  // Oracle → PostgreSQL
  [`${DialectType.ORACLE}_${DialectType.POSTGRESQL}`]: {
    title: 'Oracle → PostgreSQL 변환 가이드',
    commonIssues: [
      {
        issue: 'ROWNUM 페이징',
        oracle: 'SELECT * FROM users WHERE ROWNUM <= 10;',
        postgresql: 'SELECT * FROM users LIMIT 10;',
        note: 'ROWNUM → LIMIT으로 변경합니다. 복잡한 ROWNUM 조건은 ROW_NUMBER() OVER()로 대체.'
      },
      {
        issue: 'NVL NULL 처리',
        oracle: "NVL(email, '없음')",
        postgresql: "COALESCE(email, '없음')",
        note: 'NVL → COALESCE로 변경합니다.'
      },
      {
        issue: 'DECODE 조건 분기',
        oracle: "DECODE(status, 'A', '활성', 'I', '비활성', '기타')",
        postgresql: "CASE status WHEN 'A' THEN '활성' WHEN 'I' THEN '비활성' ELSE '기타' END",
        note: 'DECODE → CASE WHEN 구문으로 변경합니다.'
      },
      {
        issue: 'SYSDATE 현재 날짜',
        oracle: 'SELECT SYSDATE FROM DUAL;',
        postgresql: 'SELECT CURRENT_TIMESTAMP;',
        note: 'SYSDATE → CURRENT_TIMESTAMP, DUAL 테이블 불필요.'
      },
      {
        issue: 'MONTHS_BETWEEN',
        oracle: 'MONTHS_BETWEEN(end_date, start_date)',
        postgresql: "EXTRACT(YEAR FROM AGE(end_date, start_date)) * 12 + EXTRACT(MONTH FROM AGE(end_date, start_date))",
        note: 'PostgreSQL은 AGE 함수와 EXTRACT를 조합하여 월 차이를 계산합니다.'
      },
      {
        issue: 'Oracle 조인 (+)',
        oracle: "SELECT * FROM emp e, dept d WHERE e.dept_id = d.id(+);",
        postgresql: 'SELECT * FROM emp e LEFT JOIN dept d ON e.dept_id = d.id;',
        note: 'Oracle 고유 조인 문법 (+) → 표준 LEFT/RIGHT JOIN으로 변경합니다.'
      },
      {
        issue: 'SEQUENCE 사용',
        oracle: 'sequence_name.NEXTVAL',
        postgresql: "nextval('sequence_name')",
        note: 'NEXTVAL → nextval() 함수로 변경합니다.'
      }
    ]
  },
  // Oracle → MySQL
  [`${DialectType.ORACLE}_${DialectType.MYSQL}`]: {
    title: 'Oracle → MySQL 변환 가이드',
    commonIssues: [
      {
        issue: 'ROWNUM 페이징',
        oracle: 'SELECT * FROM users WHERE ROWNUM <= 10;',
        mysql: 'SELECT * FROM users LIMIT 10;',
        note: 'ROWNUM → LIMIT으로 변경합니다.'
      },
      {
        issue: 'NVL NULL 처리',
        oracle: "NVL(email, '없음')",
        mysql: "IFNULL(email, '없음')",
        note: 'NVL → IFNULL로 변경합니다.'
      },
      {
        issue: 'DECODE 조건 분기',
        oracle: "DECODE(status, 'A', '활성', 'I', '비활성', '기타')",
        mysql: "CASE status WHEN 'A' THEN '활성' WHEN 'I' THEN '비활성' ELSE '기타' END",
        note: 'DECODE → CASE WHEN 구문으로 변경합니다.'
      },
      {
        issue: 'TO_CHAR 날짜 포맷팅',
        oracle: "TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS')",
        mysql: "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')",
        note: 'TO_CHAR → DATE_FORMAT으로 변경하고 포맷 문자열을 MySQL 스타일로 변환합니다.'
      },
      {
        issue: 'SYSDATE 현재 날짜',
        oracle: 'SELECT SYSDATE FROM DUAL;',
        mysql: 'SELECT NOW();',
        note: 'SYSDATE → NOW(), DUAL 테이블 불필요.'
      },
      {
        issue: 'MONTHS_BETWEEN',
        oracle: 'MONTHS_BETWEEN(end_date, start_date)',
        mysql: 'TIMESTAMPDIFF(MONTH, start_date, end_date)',
        note: 'MONTHS_BETWEEN → TIMESTAMPDIFF로 변경. 인자 순서가 다릅니다!'
      },
      {
        issue: '문자열 연결',
        oracle: "first_name || ' ' || last_name",
        mysql: "CONCAT(first_name, ' ', last_name)",
        note: '|| 연산자 → CONCAT 함수로 변경합니다.'
      }
    ]
  },
  // PostgreSQL → MySQL
  [`${DialectType.POSTGRESQL}_${DialectType.MYSQL}`]: {
    title: 'PostgreSQL → MySQL 변환 가이드',
    commonIssues: [
      {
        issue: 'TO_CHAR 날짜 포맷팅',
        postgresql: "TO_CHAR(created_at, 'YYYY-MM-DD')",
        mysql: "DATE_FORMAT(created_at, '%Y-%m-%d')",
        note: 'TO_CHAR → DATE_FORMAT으로 변경하고 포맷 문자열을 변환합니다.'
      },
      {
        issue: 'COALESCE 사용',
        postgresql: "COALESCE(email, '없음')",
        mysql: "IFNULL(email, '없음')",
        note: 'COALESCE는 MySQL에서도 지원하지만, 2개 인자일 경우 IFNULL이 더 직관적입니다.'
      },
      {
        issue: '문자열 연결',
        postgresql: "first_name || ' ' || last_name",
        mysql: "CONCAT(first_name, ' ', last_name)",
        note: '|| 연산자 → CONCAT 함수로 변경합니다.'
      },
      {
        issue: 'ILIKE 대소문자 무시',
        postgresql: "WHERE name ILIKE '%john%'",
        mysql: "WHERE LOWER(name) LIKE LOWER('%john%')",
        note: 'ILIKE → LOWER() + LIKE 조합으로 변경합니다.'
      },
      {
        issue: 'SERIAL 자동 증가',
        postgresql: 'id SERIAL PRIMARY KEY',
        mysql: 'id INT AUTO_INCREMENT PRIMARY KEY',
        note: 'SERIAL → INT AUTO_INCREMENT로 변경합니다.'
      },
      {
        issue: 'ARRAY 타입',
        postgresql: "tags TEXT[] DEFAULT '{}'",
        mysql: 'tags JSON DEFAULT (JSON_ARRAY())',
        note: 'PostgreSQL ARRAY → MySQL JSON 타입으로 대체합니다.'
      }
    ]
  },
  // PostgreSQL → Oracle
  [`${DialectType.POSTGRESQL}_${DialectType.ORACLE}`]: {
    title: 'PostgreSQL → Oracle 변환 가이드',
    commonIssues: [
      {
        issue: 'LIMIT 페이징',
        postgresql: 'SELECT * FROM users LIMIT 10 OFFSET 20;',
        oracle: 'SELECT * FROM users OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;',
        note: 'Oracle 12c+는 OFFSET FETCH 구문을 지원합니다. 이전 버전은 ROWNUM 사용.'
      },
      {
        issue: 'COALESCE 사용',
        postgresql: "COALESCE(email, '없음')",
        oracle: "NVL(email, '없음')",
        note: 'COALESCE는 Oracle에서도 지원하지만 NVL이 Oracle 표준입니다.'
      },
      {
        issue: 'CURRENT_TIMESTAMP',
        postgresql: 'SELECT CURRENT_TIMESTAMP;',
        oracle: 'SELECT SYSDATE FROM DUAL;',
        note: 'CURRENT_TIMESTAMP → SYSDATE, FROM DUAL 추가 필요.'
      },
      {
        issue: 'SERIAL 자동 증가',
        postgresql: 'id SERIAL PRIMARY KEY',
        oracle: 'id NUMBER GENERATED AS IDENTITY PRIMARY KEY',
        note: 'SERIAL → GENERATED AS IDENTITY (Oracle 12c+) 또는 SEQUENCE + TRIGGER.'
      },
      {
        issue: 'ILIKE 대소문자 무시',
        postgresql: "WHERE name ILIKE '%john%'",
        oracle: "WHERE UPPER(name) LIKE UPPER('%john%')",
        note: 'ILIKE → UPPER() + LIKE 조합으로 변경합니다.'
      },
      {
        issue: 'BOOLEAN 타입',
        postgresql: 'is_active BOOLEAN DEFAULT TRUE',
        oracle: "is_active CHAR(1) DEFAULT 'Y' CHECK (is_active IN ('Y', 'N'))",
        note: 'Oracle은 BOOLEAN 타입을 SQL에서 지원하지 않아 CHAR(1) 또는 NUMBER(1)로 대체.'
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
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-4xl w-full mx-4 max-h-[80vh] flex flex-col">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-xl font-semibold text-gray-800 dark:text-gray-100">
            {sourceFeatures.name} → {targetFeatures.name} 변환 가이드
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 탭 네비게이션 */}
        <div className="flex border-b border-gray-200 dark:border-gray-700">
          <button
            onClick={() => setActiveTab('features')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'features'
                ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}
          >
            데이터베이스 특징
          </button>
          <button
            onClick={() => setActiveTab('guide')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'guide'
                ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
            }`}
          >
            변환 가이드
          </button>
          <button
            onClick={() => setActiveTab('tips')}
            className={`px-4 py-2 text-sm font-medium ${
              activeTab === 'tips'
                ? 'text-blue-600 dark:text-blue-400 border-b-2 border-blue-600'
                : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
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
              <div className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <div className={`w-3 h-3 rounded-full ${sourceFeatures.color}`}></div>
                  <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
                    {sourceFeatures.name}
                  </h3>
                  <span className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded">
                    {sourceFeatures.version}
                  </span>
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                  {sourceFeatures.description}
                </p>
                <div className="space-y-4">
                  <div>
                    <h4 className="font-medium text-green-600 dark:text-green-400 mb-2 text-sm">✅ 지원 기능</h4>
                    <ul className="space-y-1">
                      {sourceFeatures.features.map((feature, index) => (
                        <li key={index} className="text-xs text-gray-600 dark:text-gray-300 flex items-center">
                          <span className="w-1.5 h-1.5 bg-green-500 rounded-full mr-2"></span>
                          {feature}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4 className="font-medium text-red-600 dark:text-red-400 mb-2 text-sm">❌ 제한사항</h4>
                    <ul className="space-y-1">
                      {sourceFeatures.limitations.map((limitation, index) => (
                        <li key={index} className="text-xs text-gray-600 dark:text-gray-300 flex items-center">
                          <span className="w-1.5 h-1.5 bg-red-500 rounded-full mr-2"></span>
                          {limitation}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              {/* 타겟 데이터베이스 */}
              <div className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <div className={`w-3 h-3 rounded-full ${targetFeatures.color}`}></div>
                  <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100">
                    {targetFeatures.name}
                  </h3>
                  <span className="text-xs px-2 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 rounded">
                    {targetFeatures.version}
                  </span>
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                  {targetFeatures.description}
                </p>
                <div className="space-y-4">
                  <div>
                    <h4 className="font-medium text-green-600 dark:text-green-400 mb-2 text-sm">✅ 지원 기능</h4>
                    <ul className="space-y-1">
                      {targetFeatures.features.map((feature, index) => (
                        <li key={index} className="text-xs text-gray-600 dark:text-gray-300 flex items-center">
                          <span className="w-1.5 h-1.5 bg-green-500 rounded-full mr-2"></span>
                          {feature}
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4 className="font-medium text-red-600 dark:text-red-400 mb-2 text-sm">❌ 제한사항</h4>
                    <ul className="space-y-1">
                      {targetFeatures.limitations.map((limitation, index) => (
                        <li key={index} className="text-xs text-gray-600 dark:text-gray-300 flex items-center">
                          <span className="w-1.5 h-1.5 bg-red-500 rounded-full mr-2"></span>
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
              <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">
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
                    <div key={index} className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
                      <h4 className="font-medium text-gray-800 dark:text-gray-100 mb-3">{issue.issue}</h4>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                        <div>
                          <h5 className="text-sm font-medium text-red-600 dark:text-red-400 mb-2">원본 ({sourceFeatures.name})</h5>
                          <pre className="text-xs bg-red-50 dark:bg-red-900/30 dark:text-red-200 p-2 rounded border dark:border-red-800 overflow-x-auto">
                            <code>{sourceCode}</code>
                          </pre>
                        </div>
                        <div>
                          <h5 className="text-sm font-medium text-green-600 dark:text-green-400 mb-2">변환 ({targetFeatures.name})</h5>
                          <pre className="text-xs bg-green-50 dark:bg-green-900/30 dark:text-green-200 p-2 rounded border dark:border-green-800 overflow-x-auto">
                            <code>{targetCode}</code>
                          </pre>
                        </div>
                      </div>
                      <div className="text-sm text-gray-600 dark:text-gray-300 bg-blue-50 dark:bg-blue-900/30 p-2 rounded">
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
              <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">변환 팁</h3>
              <div className="space-y-4">
                <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                  <h4 className="font-medium text-blue-800 dark:text-blue-300 mb-2">🔍 변환 전 체크리스트</h4>
                  <ul className="text-sm text-blue-700 dark:text-blue-400 space-y-1">
                    <li>• 데이터 타입 호환성 확인</li>
                    <li>• 함수명 및 문법 차이점 파악</li>
                    <li>• 제약조건 및 인덱스 고려</li>
                    <li>• 성능에 영향을 줄 수 있는 구문 식별</li>
                  </ul>
                </div>

                <div className="bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-800 rounded-lg p-4">
                  <h4 className="font-medium text-green-800 dark:text-green-300 mb-2">✅ 변환 후 검증</h4>
                  <ul className="text-sm text-green-700 dark:text-green-400 space-y-1">
                    <li>• 변환된 SQL 문법 검증</li>
                    <li>• 데이터 타입 정확성 확인</li>
                    <li>• 성능 테스트 수행</li>
                    <li>• 예상 결과와 실제 결과 비교</li>
                  </ul>
                </div>

                <div className="bg-yellow-50 dark:bg-yellow-900/30 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
                  <h4 className="font-medium text-yellow-800 dark:text-yellow-300 mb-2">⚠️ 주의사항</h4>
                  <ul className="text-sm text-yellow-700 dark:text-yellow-400 space-y-1">
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
