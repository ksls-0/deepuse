import api from './config';

export const reportApi = {
  /** targetType: 'SET' | 'TEMPLATE' */
  submit({ targetType, targetId, reason, detail }) {
    return api.post('/api/reports', { targetType, targetId, reason, detail });
  }
};

/** 举报原因选项，前后端共用同一套文案 */
export const REPORT_REASONS = [
  '违法违规',
  '色情低俗',
  '侵犯权益',
  '广告骚扰',
  '其他'
];
