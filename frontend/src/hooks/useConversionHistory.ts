import { useState, useEffect } from 'react';
import {type ConversionResponse, type ConversionHistoryItem } from '../types';

const HISTORY_STORAGE_KEY = 'sql_conversion_history';
const MAX_HISTORY_ITEMS = 50;

export const useConversionHistory = () => {
  const [history, setHistory] = useState<ConversionHistoryItem[]>([]);

  // 로컬 스토리지에서 히스토리 로드
  useEffect(() => {
    try {
      const stored = localStorage.getItem(HISTORY_STORAGE_KEY);
      if (stored) {
        const parsedHistory = JSON.parse(stored).map((item: any) => ({
          ...item,
          timestamp: new Date(item.timestamp)
        }));
        setHistory(parsedHistory);
      }
    } catch (error) {
      console.error('Failed to load conversion history:', error);
    }
  }, []);

  // 히스토리 저장
  const saveHistory = (history: ConversionHistoryItem[]) => {
    try {
      localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(history));
    } catch (error) {
      console.error('Failed to save conversion history:', error);
    }
  };

  // 변환 결과 추가
  const addConversion = (result: ConversionResponse) => {
    const newItem: ConversionHistoryItem = {
      id: Date.now().toString(),
      timestamp: new Date(),
      originalSql: result.originalSql,
      convertedSql: result.convertedSql,
      sourceDialect: result.sourceDialect,
      targetDialect: result.targetDialect,
      warnings: result.warnings,
      appliedRules: result.appliedRules,
      conversionTime: result.conversionTime,
      success: result.success,
      error: result.error
    };

    setHistory(prev => {
      const newHistory = [newItem, ...prev].slice(0, MAX_HISTORY_ITEMS);
      saveHistory(newHistory);
      return newHistory;
    });
  };

  // 히스토리 아이템 삭제
  const removeConversion = (id: string) => {
    setHistory(prev => {
      const newHistory = prev.filter(item => item.id !== id);
      saveHistory(newHistory);
      return newHistory;
    });
  };

  // 히스토리 전체 삭제
  const clearHistory = () => {
    setHistory([]);
    localStorage.removeItem(HISTORY_STORAGE_KEY);
  };

  return {
    history,
    addConversion,
    removeConversion,
    clearHistory
  };
};
