import api from './config';

export const userApi = {
  // 验证码
  sendEmailCode(email) {
    return api.post('/user/code/email', { target: email });
  },
  sendPhoneCode(phone) {
    return api.post('/user/code/phone', { target: phone });
  },

  // 注册
  registerEmail(email, code, password) {
    return api.post('/user/register/email', { email, code, password });
  },
  registerPhone(phone, code, password) {
    return api.post('/user/register/phone', { phone, code, password });
  },

  // 登录
  loginEmail(email, password) {
    return api.post('/user/login/email', { email, password });
  },
  loginPhone(phone, password) {
    return api.post('/user/login/phone', { phone, password });
  },

  me() {
    return api.get('/user/me');
  }
};
