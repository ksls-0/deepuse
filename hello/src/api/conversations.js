import api from './config';

export const conversationApi = {
  create(templateId) {
    return api.post('/api/conversations', { templateId });
  },
  mine(page = 0, size = 20) {
    return api.get('/api/conversations', { params: { page, size } });
  },
  detail(id) {
    return api.get(`/api/conversations/${id}`);
  },
  messages(id, { beforeId = null, page = 0, size = 50 } = {}) {
    return api.get(`/api/conversations/${id}/messages`, {
      params: { ...(beforeId ? { beforeId } : {}), page, size }
    });
  },
  send(id, message) {
    return api.post(`/api/conversations/${id}/chat`, { message });
  },
  remove(id) {
    return api.delete(`/api/conversations/${id}`);
  }
};
