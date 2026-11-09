import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Room } from 'livekit-client';
import { api, connectRoomWs, currentUser } from '../api';

interface Comment { commentId: string; userId: string; nickname: string; content: string; }

export default function ControlPage() {
  const { roomId = '' } = useParams();
  const [room, setRoom] = useState<any>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [online, setOnline] = useState(0);
  const [err, setErr] = useState('');
  const [publishing, setPublishing] = useState(false);
  const lkRoomRef = useRef<Room | null>(null);
  const previewRef = useRef<HTMLDivElement>(null);

  async function load() {
    try { setRoom(await api.roomDetail(roomId)); } catch (e: any) { setErr(e.message); }
  }
  useEffect(() => { load(); }, [roomId]);

  useEffect(() => {
    const ws = connectRoomWs(roomId, {
      onEvent: (type, payload) => {
        if (type === 'auth.ok') setOnline(payload.online);
        if (type === 'comment.new') setComments(cs => [...cs.slice(-199), payload]);
        if (type === 'presence.heartbeat') setOnline(payload.online);
        if (type === 'like.batch') { /* 主播侧不显示点赞总数，可扩展 */ }
      },
    });
    return () => ws.close();
  }, [roomId]);

  async function start() {
    setErr('');
    try {
      const res = await api.startRoom(roomId);
      setRoom(res.room);
      const lk = new Room();
      await lk.connect(res.livekit.serverUrl, res.livekit.token);
      await lk.localParticipant.enableCameraAndMicrophone();
      lkRoomRef.current = lk;
      setPublishing(true);
      lk.localParticipant.videoTrackPublications.forEach(pub => {
        const el = pub.videoTrack?.attach();
        if (el && previewRef.current) previewRef.current.appendChild(el);
      });
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
      <h2>{room?.title || '直播间'} <span className="muted">在线 {online}</span></h2>
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      <div className="card">
        {room?.status !== 'LIVING' ? (
          <button onClick={start} disabled={publishing}>开播（摄像头+麦克风）</button>
        ) : (
          <div className="row">
            <span style={{ color: '#4ade80' }}>● 推流中</span>
            <button className="red" onClick={stop}>下播</button>
          </div>
        )}
        <div ref={previewRef} className="row" style={{ marginTop: 12, flexWrap: 'wrap' }} />
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
