import api from './config';

export const userApi = {
  login(account, password) {
    return api.post('/user/login', { account, password });
  },

  register(account, password) {
    return api.post('/user/register', { account, password });
  },

  me() {
    return api.get('/user/me');
  }
};
