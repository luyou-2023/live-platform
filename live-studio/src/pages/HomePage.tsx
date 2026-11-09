import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, currentUser, logout } from '../api';
import { useAuthGuard } from './useAuthGuard';

export default function HomePage() {
  useAuthGuard();
  const nav = useNavigate();
  const [rooms, setRooms] = useState<any[]>([]);
  const [title, setTitle] = useState('');
  const [err, setErr] = useState('');

  async function load() {
    try { setRooms(await api.myRooms()); } catch (e: any) { setErr(e.message); }
  }
  useEffect(() => { load(); }, []);

  async function create() {
    setErr('');
    try {
      await api.createRoom(title, '');
      setTitle('');
      await load();
    } catch (e: any) { setErr(e.message); }
  }

  const statusText: Record<string, string> = { CREATED: '未开播', LIVING: '直播中', ENDED: '已结束' };

  return (
    <div className="page">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>我的直播间</h2>
        <div className="row">
          <span className="muted">{currentUser()?.nickname}</span>
          <button className="gray" onClick={() => { logout(); nav('/login'); }}>退出</button>
        </div>
      </div>
      <div className="card row">
        <input placeholder="直播间标题" value={title} onChange={e => setTitle(e.target.value)} />
        <button onClick={create} disabled={!title.trim()}>创建直播间</button>
      </div>
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      {rooms.map(r => (
        <div className="card" key={r.roomId}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <div>
              <b>{r.title}</b> <span className="muted">#{statusText[r.status] || r.status}</span>
            </div>
            <Link to={`/control/${r.roomId}`}><button>进入控制台</button></Link>
          </div>
        </div>
      ))}
      {rooms.length === 0 && <div className="muted">还没有直播间，先创建一个</div>}
    </div>
  );
}
