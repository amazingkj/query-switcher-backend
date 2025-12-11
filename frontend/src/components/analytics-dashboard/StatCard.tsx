import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: React.ReactNode;
  colorScheme: 'blue' | 'green' | 'yellow' | 'purple';
}

const colorClasses = {
  blue: {
    bg: 'bg-blue-50',
    border: 'border-blue-200',
    iconBg: 'bg-blue-100',
    iconText: 'text-blue-600',
    titleText: 'text-blue-600',
    valueText: 'text-blue-900',
  },
  green: {
    bg: 'bg-green-50',
    border: 'border-green-200',
    iconBg: 'bg-green-100',
    iconText: 'text-green-600',
    titleText: 'text-green-600',
    valueText: 'text-green-900',
  },
  yellow: {
    bg: 'bg-yellow-50',
    border: 'border-yellow-200',
    iconBg: 'bg-yellow-100',
    iconText: 'text-yellow-600',
    titleText: 'text-yellow-600',
    valueText: 'text-yellow-900',
  },
  purple: {
    bg: 'bg-purple-50',
    border: 'border-purple-200',
    iconBg: 'bg-purple-100',
    iconText: 'text-purple-600',
    titleText: 'text-purple-600',
    valueText: 'text-purple-900',
  },
};

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  icon,
  colorScheme,
}) => {
  const colors = colorClasses[colorScheme];

  return (
    <div className={`${colors.bg} p-4 rounded-lg border ${colors.border}`}>
      <div className="flex items-center">
        <div className={`p-2 ${colors.iconBg} rounded-lg`}>
          <div className={`w-6 h-6 ${colors.iconText}`}>{icon}</div>
        </div>
        <div className="ml-4">
          <p className={`text-sm font-medium ${colors.titleText}`}>{title}</p>
          <p className={`text-2xl font-bold ${colors.valueText}`}>{value}</p>
        </div>
      </div>
    </div>
  );
};

// 아이콘 컴포넌트들
export const ChartIcon: React.FC = () => (
  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
    />
  </svg>
);

export const CheckCircleIcon: React.FC = () => (
  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

export const ClockIcon: React.FC = () => (
  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
    />
  </svg>
);

export const LightningIcon: React.FC = () => (
  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M13 10V3L4 14h7v7l9-11h-7z"
    />
  </svg>
);