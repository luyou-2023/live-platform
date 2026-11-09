import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, saveSession } from '../api';

export default function LoginPage() {
  const nav = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [err, setErr] = useState('');

  async function submit() {
    setErr('');
    try {
      const data = mode === 'login'
        ? await api.login(username, password)
        : await api.register(username, password, nickname);
      saveSession(data.token, data.user);
      nav('/');
    } catch (e: any) { setErr(e.message); }
  }

  return (
    <div className="login-box card">
      <h2>LiveStudio 主播端</h2>
      <input placeholder="用户名" value={username} onChange={e => setUsername(e.target.value)} />
      <input placeholder="密码（至少6位）" type="password" value={password} onChange={e => setPassword(e.target.value)} />
      {mode === 'register' && <input placeholder="昵称" value={nickname} onChange={e => setNickname(e.target.value)} />}
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      <button onClick={submit}>{mode === 'login' ? '登录' : '注册并登录'}</button>
      <a onClick={() => setMode(mode === 'login' ? 'register' : 'login')} style={{ cursor: 'pointer' }}>
        {mode === 'login' ? '没有账号？去注册' : '已有账号？去登录'}
      </a>
    </div>
  );
}
