const BASE = 'http://127.0.0.1:8080';

export function token(): string | null { return localStorage.getItem('lp_token'); }
export function currentUser(): any { return JSON.parse(localStorage.getItem('lp_user') || 'null'); }
export function saveSession(t: string, user: any) {
  localStorage.setItem('lp_token', t);
  localStorage.setItem('lp_user', JSON.stringify(user));
}
export function logout() { localStorage.removeItem('lp_token'); localStorage.removeItem('lp_user'); }

async function req(method: string, path: string, body?: any): Promise<any> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const t = token();
  if (t) headers['Authorization'] = 'Bearer ' + t;
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const env = await res.json();
  if (env.code === 40100) {
    localStorage.removeItem('lp_token');
    localStorage.removeItem('lp_user');
    if (!location.pathname.startsWith('/login')) location.href = '/login';
    throw new Error('登录已过期，请重新登录');
  }
  if (env.code !== 0) throw new Error(env.message || ('code ' + env.code));
  return env.data;
}

export const api = {
  register: (username: string, password: string, nickname: string) => req('POST', '/api/v1/users/register', { username, password, nickname }),
  login: (username: string, password: string) => req('POST', '/api/v1/users/login', { username, password }),
  createRoom: (title: string, coverUrl: string) => req('POST', '/api/v1/rooms', { title, coverUrl }),
  myRooms: () => req('GET', '/api/v1/rooms/mine'),
  startRoom: (roomId: string) => req('POST', `/api/v1/rooms/${roomId}/start`),
  stopRoom: (roomId: string) => req('POST', `/api/v1/rooms/${roomId}/stop`),
  roomDetail: (roomId: string) => req('GET', `/api/v1/rooms/${roomId}`),
  mute: (roomId: string, userId: string, minutes: number) => req('POST', `/api/v1/rooms/${roomId}/mute`, { userId, minutes }),
  liveList: () => req('GET', '/api/v1/rooms/live'),
  enter: (roomId: string) => req('POST', `/api/v1/rooms/${roomId}/enter`),
  follow: (userId: string) => req('POST', `/api/v1/users/${userId}/follow`),
  unfollow: (userId: string) => req('DELETE', `/api/v1/users/${userId}/follow`),
  counts: (userId: string) => req('GET', `/api/v1/users/${userId}/follow/counts`),
};

export interface WsHandlers {
  onEvent: (type: string, payload: any) => void;
  onOpen?: () => void;
  onClose?: () => void;
}

export function connectRoomWs(roomId: string, handlers: WsHandlers): WebSocket {
  const ws = new WebSocket(`ws://127.0.0.1:8080/ws/v1/rooms/${roomId}`);
  ws.onopen = () => { ws.send(JSON.stringify({ type: 'auth', payload: { token: token() } })); handlers.onOpen?.(); };
  ws.onmessage = (ev) => {
    const frame = JSON.parse(ev.data);
    handlers.onEvent(frame.type, frame.payload);
  };
  ws.onclose = () => handlers.onClose?.();
  return ws;
}
