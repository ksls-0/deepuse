import api from './config';

export const templateApi = {
  mine(page = 0, size = 12) {
    return api.get('/api/templates/mine', { params: { page, size } });
  },
  detail(id) {
    return api.get(`/api/templates/${id}`);
  },
  update(id, payload) {
    return api.put(`/api/templates/${id}`, payload);
  },
  remove(id) {
    return api.delete(`/api/templates/${id}`);
  },
  setVisibility(id, visibility) {
    return api.patch(`/api/templates/${id}/visibility`, { visibility });
  },

  // 广场
  explore({ keyword = '', sort = 'latest', page = 0, size = 12 } = {}) {
    return api.get('/api/explore/templates', { params: { keyword, sort, page, size } });
  },
  exploreDetail(id) {
    return api.get(`/api/explore/templates/${id}`);
  }
};
