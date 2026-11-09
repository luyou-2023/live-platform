import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Room } from 'livekit-client';
import { api, connectRoomWsAutoReload, currentUser } from '../api';
import { useAuthGuard } from './useAuthGuard';

interface Comment { commentId: string; userId: string; nickname: string; content: string; }

export default function ControlPage() {
  useAuthGuard();
  const { roomId = '' } = useParams();
  const [room, setRoom] = useState<any>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [online, setOnline] = useState(0);
  const [likes, setLikes] = useState(0);
  const [obs, setObs] = useState<{ protocol: string; url: string; streamKey: string } | null>(null);
  const [obsProto, setObsProto] = useState<'RTMP' | 'WHIP'>('RTMP');
  const [err, setErr] = useState('');
  const [publishing, setPublishing] = useState(false);
  const lkRoomRef = useRef<Room | null>(null);
  const previewRef = useRef<HTMLDivElement>(null);

  async function load() {
    try {
      const r = await api.roomDetail(roomId);
      setRoom(r);
      if (r.status === 'LIVING') {
        const snap = await api.snapshot(roomId);
        setOnline(snap.online);
        setLikes(snap.likes);
        setComments(snap.comments);
        api.obsInfo(roomId, obsProto).then(setObs).catch(() => {});
      }
    } catch (e: any) { setErr(e.message); }
  }
  useEffect(() => { load(); }, [roomId]);
  // 协议切换时重新取推流信息
  useEffect(() => {
    if (living) api.obsInfo(roomId, obsProto).then(setObs).catch(() => setObs(null));
  }, [obsProto]);

  // 仅在直播中连接互动 WS：未开播时连了也会被 auth.fail 拒绝
  const living = room?.status === 'LIVING';
  useEffect(() => {
    if (!living) return;
    const ws = connectRoomWsAutoReload(roomId, {
      onEvent: (type, payload) => {
        if (type === 'auth.ok') setOnline(payload.online);
        if (type === 'comment.new') setComments(cs => [...cs.slice(-199), payload]);
        if (type === 'presence.heartbeat') setOnline(payload.online);
        if (type === 'like.batch') setLikes(payload.total);
        if (type === 'room.ended') setRoom((r: any) => r ? { ...r, status: 'ENDED' } : r);
      },
    });
    return () => ws.close();
  }, [roomId, living]);

  async function connectAndPublish(livekit: any) {
    const lk = new Room();
    await lk.connect(livekit.serverUrl, livekit.token);
    await lk.localParticipant.enableCameraAndMicrophone();
    lkRoomRef.current = lk;
    (window as any).__lkRoom = lk;
    setPublishing(true);
    lk.localParticipant.videoTrackPublications.forEach(pub => {
      const el = pub.videoTrack?.attach();
      if (el && previewRef.current) previewRef.current.appendChild(el);
    });
  }

  async function start() {
    setErr('');
    try {
      const res = await api.startRoom(roomId);
      setRoom(res.room);
      if (res.obs) setObs(res.obs);
      await connectAndPublish(res.livekit);
    } catch (e: any) { setErr(e.message); }
  }

  // 页面刷新后房间仍直播中但本机推流已断：补签 token 重新推流
  async function republish() {
    setErr('');
    try {
      const res = await api.republishRoom(roomId);
      if (res.obs) setObs(res.obs);
      await connectAndPublish(res.livekit);
    } catch (e: any) { setErr(e.message); }
  }

  async function stop() {
    setErr('');
    try {
      await api.stopRoom(roomId);
      lkRoomRef.current?.disconnect();
      lkRoomRef.current = null;
      setPublishing(false);
      await load();
    } catch (e: any) { setErr(e.message); }
  }

  async function mute(userId: string) {
    try { await api.mute(roomId, userId, 10); } catch (e: any) { setErr(e.message); }
  }

  return (
    <div className="page">
      <h2>{room?.title || '直播间'} <span className="muted">在线 {online} · 赞 {likes}</span></h2>
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      <div className="card">
        {room?.status !== 'LIVING' ? (
          <button onClick={start} disabled={publishing}>{room?.status === 'ENDED' ? '重新开播（摄像头+麦克风）' : '开播（摄像头+麦克风）'}</button>
        ) : publishing ? (
          <div className="row">
            <span style={{ color: '#4ade80' }}>● 推流中</span>
            <button className="red" onClick={stop}>下播</button>
          </div>
        ) : (
          <div className="row">
            <span style={{ color: '#fbbf24' }}>房间直播中，但本机推流已断开（页面刷新导致）</span>
            <button onClick={republish}>重新推流</button>
          </div>
        )}
        <div ref={previewRef} className="row" style={{ marginTop: 12, flexWrap: 'wrap' }} />
        {obs && (
          <div style={{ marginTop: 14, borderTop: '1px solid #262b3a', paddingTop: 12 }}>
            <b>OBS 推流</b>
            <label style={{ marginLeft: 16 }}>
              <input type="radio" name="obsproto" checked={obsProto === 'RTMP'}
                onChange={() => setObsProto('RTMP')} /> RTMP
            </label>
            <label style={{ marginLeft: 12 }}>
              <input type="radio" name="obsproto" checked={obsProto === 'WHIP'}
                onChange={() => setObsProto('WHIP')} /> WHIP（OBS 30+ 低延迟）
            </label>
            <div className="row" style={{ marginTop: 8 }}>
              <span className="muted" style={{ width: 80 }}>服务器</span>
              <input readOnly value={obs.url} onFocus={e => e.target.select()} style={{ flex: 1 }} />
            </div>
            <div className="row" style={{ marginTop: 8 }}>
              <span className="muted" style={{ width: 80 }}>{obsProto === 'WHIP' ? 'Bearer 令牌' : '串流密钥'}</span>
              <input readOnly value={obs.streamKey} onFocus={e => e.target.select()} style={{ flex: 1 }} />
            </div>
            <div className="muted" style={{ marginTop: 6 }}>
              {obsProto === 'WHIP'
                ? 'OBS 设置 → 直播：服务类型选「WHIP」，服务器与 Bearer 令牌填上面两项。'
                : 'OBS 设置 → 直播：服务选「自定义」，服务器与串流密钥填上面两项。'}
            </div>
            <div className="muted" style={{ marginTop: 6 }}>用摄像头开播和 OBS 推流二选一；两边同时推观众会同时看到两路画面。</div>
          </div>
        )}
      </div>
      <div className="card">
        <b>评论管理</b> <span className="muted">（点「禁言10分」管理评论用户）</span>
        <div className="comments" style={{ marginTop: 8 }}>
          {comments.map(c => (
            <div className="comment row" key={c.commentId} style={{ justifyContent: 'space-between' }}>
              <span><b>{c.nickname}</b>：{c.content}</span>
              {c.userId !== currentUser()?.userId && (
                <button className="gray" style={{ padding: '2px 8px', fontSize: 12 }} onClick={() => mute(c.userId)}>禁言10分</button>
              )}
            </div>
          ))}
          {comments.length === 0 && <div className="muted">暂无评论</div>}
        </div>
      </div>
    </div>
  );
}
