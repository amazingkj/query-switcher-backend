import React from 'react';
import type { EventsTabProps } from './types';

// 빈 상태 아이콘
const EmptyIcon: React.FC = () => (
  <svg
    className="w-12 h-12 mx-auto mb-4 text-gray-300"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
    />
  </svg>
);

export const EventsTab: React.FC<EventsTabProps> = ({ customEvents }) => {
  return (
    <div className="space-y-4">
      <h4 className="text-lg font-semibold text-gray-800">이벤트 로그</h4>
      <div className="bg-white border border-gray-200 rounded-lg max-h-96 overflow-y-auto">
        {customEvents.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <EmptyIcon />
            <p>아직 기록된 이벤트가 없습니다.</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {customEvents
              .slice(-50)
              .reverse()
              .map((event, index) => (
                <div key={index} className="p-4 hover:bg-gray-50">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {event.category} - {event.action}
                      </p>
                      {event.label && (
                        <p className="text-sm text-gray-600">{event.label}</p>
                      )}
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-gray-500">
                        {new Date(
                          event.custom_parameters?.timestamp || 0
                        ).toLocaleString()}
                      </p>
                      {event.value && (
                        <p className="text-sm font-medium text-blue-600">
                          {event.value}ms
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ))}
          </div>
        )}
      </div>
    </div>
  );
};