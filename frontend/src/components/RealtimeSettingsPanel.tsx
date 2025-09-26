import React from 'react';
import { useSqlStore } from '../stores/sqlStore';

interface RealtimeSettingsPanelProps {
  isOpen: boolean;
  onClose: () => void;
}

export const RealtimeSettingsPanel: React.FC<RealtimeSettingsPanelProps> = ({
  isOpen,
  onClose
}) => {
  const {
    isAutoConvert,
    setAutoConvert
  } = useSqlStore();

  const [settings, setSettings] = React.useState({
    autoConvert: isAutoConvert,
    debounceDelay: 1000,
    showWarnings: true,
    showConversionTime: true,
    enableHistory: true
  });

  const handleSave = () => {
    setAutoConvert(settings.autoConvert);
    // 다른 설정들도 저장 (로컬 스토리지 등)
    localStorage.setItem('sql_converter_settings', JSON.stringify(settings));
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        {/* 헤더 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-xl font-semibold text-gray-800">실시간 변환 설정</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 설정 내용 */}
        <div className="p-4 space-y-4">
          {/* 자동 변환 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium text-gray-700">자동 변환</label>
              <p className="text-xs text-gray-500">SQL 입력 시 자동으로 변환합니다</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.autoConvert}
                onChange={(e) => setSettings(prev => ({ ...prev, autoConvert: e.target.checked }))}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>

          {/* 디바운스 지연 */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              변환 지연 시간 (ms)
            </label>
            <input
              type="number"
              min="100"
              max="5000"
              step="100"
              value={settings.debounceDelay}
              onChange={(e) => setSettings(prev => ({ ...prev, debounceDelay: parseInt(e.target.value) }))}
              className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            <p className="text-xs text-gray-500 mt-1">
              입력 후 변환까지의 대기 시간입니다
            </p>
          </div>

          {/* 경고 표시 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium text-gray-700">경고 표시</label>
              <p className="text-xs text-gray-500">변환 경고를 표시합니다</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.showWarnings}
                onChange={(e) => setSettings(prev => ({ ...prev, showWarnings: e.target.checked }))}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>

          {/* 변환 시간 표시 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium text-gray-700">변환 시간 표시</label>
              <p className="text-xs text-gray-500">변환에 걸린 시간을 표시합니다</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.showConversionTime}
                onChange={(e) => setSettings(prev => ({ ...prev, showConversionTime: e.target.checked }))}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>

          {/* 히스토리 저장 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium text-gray-700">변환 히스토리 저장</label>
              <p className="text-xs text-gray-500">변환 결과를 히스토리에 저장합니다</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.enableHistory}
                onChange={(e) => setSettings(prev => ({ ...prev, enableHistory: e.target.checked }))}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-blue-600"></div>
            </label>
          </div>
        </div>

        {/* 푸터 */}
        <div className="flex items-center justify-end gap-2 p-4 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 focus:ring-2 focus:ring-blue-500"
          >
            취소
          </button>
          <button
            onClick={handleSave}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 focus:ring-2 focus:ring-blue-500"
          >
            저장
          </button>
        </div>
      </div>
    </div>
  );
};
