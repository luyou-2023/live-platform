import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, currentUser, logout } from '../api';

export default function DiscoverPage() {
  const nav = useNavigate();
  const [rooms, setRooms] = useState<any[]>([]);
  const [err, setErr] = useState('');

  async function load() {
    try { setRooms(await api.liveList()); } catch (e: any) { setErr(e.message); }
  }
  useEffect(() => {
    if (!currentUser()) { nav('/login'); return; }
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="page">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>正在直播</h2>
        <div className="row">
          <span className="muted">{currentUser()?.nickname}</span>
          <button className="gray" onClick={() => { logout(); nav('/login'); }}>退出</button>
        </div>
      </div>
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      {rooms.map(r => (
        <div className="card" key={r.roomId}>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <div>
              <b>{r.title}</b>
              <div className="muted">主播：{r.anchorNickname} · 开播于 {r.startedAt?.slice(11, 19)}</div>
            </div>
            <Link to={`/watch/${r.roomId}`}><button>进入直播间</button></Link>
          </div>
        </div>
      ))}
      {rooms.length === 0 && !err && <div className="muted">暂时没有直播，等主播开播再来</div>}
    </div>
  );
}
