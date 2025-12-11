import React from 'react';

// 팁 카드 컴포넌트
interface TipCardProps {
  title: string;
  icon: string;
  items: string[];
  colorScheme: 'blue' | 'green' | 'yellow';
}

const TipCard: React.FC<TipCardProps> = ({ title, icon, items, colorScheme }) => {
  const colorClasses = {
    blue: {
      bg: 'bg-blue-50 dark:bg-blue-900/30',
      border: 'border-blue-200 dark:border-blue-800',
      title: 'text-blue-800 dark:text-blue-300',
      text: 'text-blue-700 dark:text-blue-400',
    },
    green: {
      bg: 'bg-green-50 dark:bg-green-900/30',
      border: 'border-green-200 dark:border-green-800',
      title: 'text-green-800 dark:text-green-300',
      text: 'text-green-700 dark:text-green-400',
    },
    yellow: {
      bg: 'bg-yellow-50 dark:bg-yellow-900/30',
      border: 'border-yellow-200 dark:border-yellow-800',
      title: 'text-yellow-800 dark:text-yellow-300',
      text: 'text-yellow-700 dark:text-yellow-400',
    },
  };

  const colors = colorClasses[colorScheme];

  return (
    <div className={`${colors.bg} border ${colors.border} rounded-lg p-4`}>
      <h4 className={`font-medium ${colors.title} mb-2`}>
        {icon} {title}
      </h4>
      <ul className={`text-sm ${colors.text} space-y-1`}>
        {items.map((item, index) => (
          <li key={index}>• {item}</li>
        ))}
      </ul>
    </div>
  );
};

export const ConversionTipsTab: React.FC = () => {
  return (
    <div>
      <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">
        변환 팁
      </h3>
      <div className="space-y-4">
        <TipCard
          title="변환 전 체크리스트"
          icon="🔍"
          colorScheme="blue"
          items={[
            '데이터 타입 호환성 확인',
            '함수명 및 문법 차이점 파악',
            '제약조건 및 인덱스 고려',
            '성능에 영향을 줄 수 있는 구문 식별',
          ]}
        />

        <TipCard
          title="변환 후 검증"
          icon="✅"
          colorScheme="green"
          items={[
            '변환된 SQL 문법 검증',
            '데이터 타입 정확성 확인',
            '성능 테스트 수행',
            '예상 결과와 실제 결과 비교',
          ]}
        />

        <TipCard
          title="주의사항"
          icon="⚠️"
          colorScheme="yellow"
          items={[
            '자동 변환으로 해결되지 않는 부분은 수동 검토 필요',
            '데이터베이스별 최적화 기법 고려',
            '트랜잭션 및 동시성 처리 방식 차이',
            '에러 처리 및 예외 상황 대응',
          ]}
        />
      </div>
    </div>
  );
};