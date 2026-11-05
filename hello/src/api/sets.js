import api from './config';

export const setApi = {
  // 我的模板
  create(payload) {
    return api.post('/api/sets', payload);
  },
  mine(page = 0, size = 12) {
    return api.get('/api/sets/mine', { params: { page, size } });
  },
  detail(id) {
    return api.get(`/api/sets/${id}`);
  },
  update(id, payload) {
    return api.put(`/api/sets/${id}`, payload);
  },
  remove(id) {
    return api.delete(`/api/sets/${id}`);
  },
  setVisibility(id, visibility) {
    return api.patch(`/api/sets/${id}/visibility`, { visibility });
  },
  // 一键导入公开模板
  importSet(publicSetId) {
    return api.post(`/api/sets/import/${publicSetId}`);
  },

  // 答卷（一人可多份）
  listSheets(setId) {
    return api.get(`/api/sets/${setId}/sheets`);
  },
  createSheet(setId) {
    return api.post(`/api/sets/${setId}/sheets`);
  },
  getSheet(setId, sheetId) {
    return api.get(`/api/sets/${setId}/sheet`, {
      params: sheetId ? { sheetId } : {}
    });
  },
  saveSheet(setId, sheetId, payload) {
    return api.put(`/api/sets/${setId}/sheet`, payload, {
      params: sheetId ? { sheetId } : {}
    });
  },
  // 同步生成角色
  generate(setId, sheetId) {
    return api.post(`/api/sets/${setId}/generate`, sheetId ? { sheetId } : {});
  },

  // 广场
  exploreSets({ keyword = '', sort = 'latest', page = 0, size = 12 } = {}) {
    return api.get('/api/explore/sets', { params: { keyword, sort, page, size } });
  },
  exploreSetDetail(id) {
    return api.get(`/api/explore/sets/${id}`);
  }
};
