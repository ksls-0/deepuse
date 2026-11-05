import api from './config';

export const adminApi = {
  stats() {
    return api.get('/api/admin/stats');
  },
  pendingReports(page = 0, size = 20) {
    return api.get('/api/admin/reports/pending', { params: { page, size } });
  },
  handledReports(page = 0, size = 20) {
    return api.get('/api/admin/reports/handled', { params: { page, size } });
  },
  /** action: 'TAKEDOWN' | 'REJECT' */
  handleReport(id, action, note) {
    return api.post(`/api/admin/reports/${id}/handle`, { action, note });
  },
  takeDown(targetType, targetId, reason) {
    return api.post(`/api/admin/${targetType}/${targetId}/takedown`, { reason });
  },
  restore(targetType, targetId) {
    return api.post(`/api/admin/${targetType}/${targetId}/restore`);
  }
};
