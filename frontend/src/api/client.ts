import axios from 'axios';

export const client = axios.create({ baseURL: '/' });

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      window.location.href = '/oauth2/authorization/keycloak';
      return new Promise(() => {}); // 阻断后续处理,页面即将跳转
    }
    return Promise.reject(err);
  }
);

export async function fetchMe() {
  const { data } = await client.get('/api/me');
  return data;
}

export function login() {
  window.location.href = '/oauth2/authorization/keycloak';
}

export function logout() {
  // 后端 /logout 由 Spring Security 处理,登出后重定向回前端
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  document.body.appendChild(form);
  form.submit();
}
