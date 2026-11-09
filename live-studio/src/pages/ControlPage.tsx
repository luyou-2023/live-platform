import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Room, RoomEvent } from 'livekit-client';
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
  /** 推流方式：开播前选择，直播中据此渲染（摄像头=本机推流，RTMP=OBS 推流） */
  const [mode, setMode] = useState<'WEBRTC' | 'RTMP' | null>(null);
  const modeKey = `lp_mode_${roomId}`;
  const [err, setErr] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [starting, setStarting] = useState(false);
  const lkRoomRef = useRef<Room | null>(null);
  const previewRef = useRef<HTMLDivElement>(null);

  async function load() {
    try {
      const r = await api.roomDetail(roomId);
      setRoom(r);
      if (r.status === 'LIVING') {
        // 刷新恢复：记住开播时选择的推流方式；没记住（新会话）则让用户先选
        const saved = sessionStorage.getItem(modeKey);
        if (saved === 'RTMP') {
          setMode('RTMP');
          api.obsInfo(roomId, obsProto).then(setObs).catch(() => setObs(null));
        } else if (saved === 'WEBRTC') {
          setMode('WEBRTC');
        }
        const snap = await api.snapshot(roomId);
        setOnline(snap.online);
        setLikes(snap.likes);
        setComments(snap.comments);
      }
    } catch (e: any) { setErr(e.message); }
  }
  useEffect(() => { load(); }, [roomId]);
  // 协议切换时重新取推流信息（仅 OBS 模式）
  useEffect(() => {
    if (living && mode === 'RTMP') api.obsInfo(roomId, obsProto).then(setObs).catch(() => setObs(null));
  }, [obsProto, mode]);

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
    // 关键：先断开旧连接。重复点「重新推流」不断开的话，
    // 同身份新连接会把旧连接踢下线（观众黑屏），且旧连接还占着摄像头导致新连接取流失败
    if (lkRoomRef.current) {
      lkRoomRef.current.disconnect();
      lkRoomRef.current = null;
    }
    const lk = new Room();
    try {
      await lk.connect(livekit.serverUrl, livekit.token);
      try {
        await lk.localParticipant.enableCameraAndMicrophone();
      } catch (e: any) {
        // 取设备失败：给出可读原因，断开空连接
        lk.disconnect();
        const name = e?.name || '';
        if (name === 'NotAllowedError') throw new Error('摄像头/麦克风权限被拒绝：点浏览器地址栏左侧的摄像头图标，选择「允许」后重试');
        if (name === 'NotFoundError') throw new Error('没有找到摄像头/麦克风设备');
        if (name === 'NotReadableError' || name === 'AbortError') throw new Error('摄像头/麦克风被其他应用（OBS、微信等）占用，关闭占用程序后重试');
        throw new Error('打开摄像头/麦克风失败：' + (e?.message || e));
      }
    } catch (e: any) {
      lk.disconnect();
      throw e;
    }
    lkRoomRef.current = lk;
    (window as any).__lkRoom = lk;
    setPublishing(true);
    // 连接被服务器断开（如同一账号在别处推流）时明确提示
    lk.on(RoomEvent.Disconnected, (reason: any) => {
      if (lkRoomRef.current !== lk) return; // 已主动断开/切换
      lkRoomRef.current = null;
      setPublishing(false);
      const r = String(reason ?? '');
      if (r.includes('DUPLICATE')) {
        setErr('同一账号在另一个页面或设备上推流，本机已被挤下线。关掉另一个页面后点「重新推流」。');
      } else {
        setErr('推流连接已断开，点「重新推流」恢复。');
      }
    });
    // 摄像头/麦克风被系统或其他应用（OBS、微信等）抢走时， track 会 ended：明确提示而非无声黑屏
    lk.localParticipant.trackPublications.forEach(pub => {
      pub.track?.mediaStreamTrack?.addEventListener('ended', () => {
        lkRoomRef.current = null;
        setPublishing(false);
        setErr('摄像头/麦克风已停止：可能被 OBS、微信等应用占用。关闭占用程序后点「重新推流」恢复。');
      });
    });
    lk.localParticipant.videoTrackPublications.forEach(pub => {
      const el = pub.videoTrack?.attach();
      if (el && previewRef.current) previewRef.current.appendChild(el);
    });
  }

  /** 选择推流方式后开播：WebRTC=本机摄像头，RTMP=OBS 推流只显示推流地址 */
  async function start(chosen: 'WEBRTC' | 'RTMP') {
    setErr('');
    if (starting) return;
    setStarting(true);
    setMode(chosen);
    try {
      const res = await api.startRoom(roomId);
      sessionStorage.setItem(modeKey, chosen);
      setRoom(res.room);
      if (chosen === 'RTMP') {
        if (res.obs) setObs(res.obs);
      } else {
        await connectAndPublish(res.livekit);
      }
    } catch (e: any) { setErr(e.message); }
    finally { setStarting(false); }
  }

  // 页面刷新后房间仍直播中但本机推流已断：补签 token 重新推流（WebRTC 路径）
  async function republish() {
    setErr('');
    try {
      const res = await api.republishRoom(roomId);
      setMode('WEBRTC');
      sessionStorage.setItem(modeKey, 'WEBRTC');
      await connectAndPublish(res.livekit);
    } catch (e: any) { setErr(e.message); }
  }

  /** 已进入进行中的直播：选择当前实际使用的推流方式 */
  async function chooseLiving(chosen: 'WEBRTC' | 'RTMP') {
    setErr('');
    if (chosen === 'WEBRTC') { await republish(); return; }
    setMode('RTMP');
    sessionStorage.setItem(modeKey, 'RTMP');
    try { setObs(await api.obsInfo(roomId, obsProto)); }
    catch (e: any) { setErr(e.message); }
  }

  /** 切回推流方式选择（本机摄像头断开时改 OBS，或反之） */
  function switchMode() {
    lkRoomRef.current?.disconnect();
    lkRoomRef.current = null;
    setPublishing(false);
    setObs(null);
    setErr('');
    sessionStorage.removeItem(modeKey);
    setMode(null);
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
          <div>
            <div className="muted" style={{ marginBottom: 10 }}>1. 选择推流方式：</div>
            <div className="row" style={{ gap: 12, alignItems: 'stretch' }}>
              {([
                ['WEBRTC', '摄像头+麦克风（WebRTC）', '浏览器直接推摄像头画面，点开播即用'],
                ['RTMP', 'OBS 推流（RTMP/WHIP）', '用 OBS 推专业画面，开播后页面显示服务器与串流密钥'],
              ] as const).map(([value, title, desc]) => (
                <div key={value} onClick={() => setMode(value)} style={{
                  flex: 1, padding: '12px 14px', borderRadius: 8, cursor: 'pointer',
                  border: mode === value ? '2px solid #4ade80' : '2px solid #262b3a',
                  background: mode === value ? '#12261a' : 'transparent',
                }}>
                  <b>{title}</b>
                  <div className="muted" style={{ marginTop: 6, fontSize: 13 }}>{desc}</div>
                </div>
              ))}
            </div>
            <div className="row" style={{ marginTop: 14 }}>
              <button onClick={() => mode && start(mode)} disabled={mode === null || starting}>
                2. {starting ? '开播中…' : room?.status === 'ENDED' ? '重新开播' : '开始直播'}
              </button>
              {mode === null && <span className="muted">← 先点上方选择一种推流方式</span>}
            </div>
          </div>
        ) : mode === 'RTMP' ? (
          <div className="row">
            <span style={{ color: '#4ade80' }}>● 直播中（等待 OBS 推流）</span>
            <button className="red" onClick={stop}>下播</button>
            <button className="gray" onClick={switchMode}>切换推流方式</button>
          </div>
        ) : mode === null ? (
          <div>
            <div className="muted" style={{ marginBottom: 10 }}>该房间正在直播中，选择当前使用的推流方式：</div>
            <div className="row" style={{ gap: 12, alignItems: 'stretch' }}>
              {([
                ['WEBRTC', '摄像头+麦克风（WebRTC）', '本机浏览器推流：选这个重新推摄像头画面'],
                ['RTMP', 'OBS 推流（RTMP/WHIP）', 'OBS 在推：选这个查看服务器与串流密钥'],
              ] as const).map(([value, title, desc]) => (
                <div key={value} onClick={() => chooseLiving(value)} style={{
                  flex: 1, padding: '12px 14px', borderRadius: 8, cursor: 'pointer',
                  border: '2px solid #262b3a',
                }}>
                  <b>{title}</b>
                  <div className="muted" style={{ marginTop: 6, fontSize: 13 }}>{desc}</div>
                </div>
              ))}
            </div>
          </div>
        ) : publishing ? (
          <div className="row">
            <span style={{ color: '#4ade80' }}>● 推流中</span>
            <button className="red" onClick={stop}>下播</button>
            <button className="gray" onClick={switchMode}>切换推流方式</button>
          </div>
        ) : (
          <div className="row">
            <span style={{ color: '#fbbf24' }}>房间直播中，但本机推流已断开</span>
            <button onClick={republish}>重新推流</button>
            <button className="gray" onClick={switchMode}>切换推流方式</button>
          </div>
        )}
        {mode !== 'RTMP' && <div ref={previewRef} className="row" style={{ marginTop: 12, flexWrap: 'wrap' }} />}
        {mode === 'RTMP' && room?.status === 'LIVING' && (
          obs ? (
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
            </div>
          ) : (
            <div className="muted" style={{ marginTop: 12, color: '#fbbf24' }}>
              推流地址获取失败，刷新页面重试。
            </div>
          )
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
