import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Room } from 'livekit-client';
import { api, connectRoomWsAutoReload } from '../api';
import { useAuthGuard } from './useAuthGuard';

interface Comment { commentId: string; userId: string; nickname: string; content: string; }

export default function WatchPage() {
  useAuthGuard();
  const { roomId = '' } = useParams();
  const nav = useNavigate();
  const [room, setRoom] = useState<any>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [online, setOnline] = useState(0);
  const [likes, setLikes] = useState(0);
  const [input, setInput] = useState('');
  const [err, setErr] = useState('');
  const [muted, setMuted] = useState(false);
  const [following, setFollowing] = useState(false);
  const stageRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let ws: WebSocket | null = null;
    let lk: Room | null = null;
    let closed = false;
    (async () => {
      try {
        const res = await api.enter(roomId);
        if (closed) return;
        setRoom(res.room);
        api.counts(res.room.anchorId).then(c => setFollowing(c.followed)).catch(() => {});
        lk = new Room();
        lk.on('trackSubscribed', (track) => {
          stageRef.current?.appendChild(track.attach());
        });
        await lk.connect(res.livekit.serverUrl, res.livekit.token);
        (window as any).__lkRoom = lk;
        ws = connectRoomWsAutoReload(roomId, {
          onEvent: (type, payload) => {
            if (type === 'auth.ok') setOnline(payload.online);
            if (type === 'comment.new') setComments(cs => [...cs.slice(-199), payload]);
            if (type === 'presence.heartbeat') setOnline(payload.online);
            if (type === 'like.batch') setLikes(payload.total);
            if (type === 'comment.muted') { setMuted(true); setTimeout(() => setMuted(false), 3000); }
            if (type === 'room.ended') nav('/');
          },
        });
        wsRef.current = ws;
      } catch (e: any) {
        setErr(e.message);
        // 进房失败（房间已下播/不存在）：3 秒后回直播列表
        setTimeout(() => nav('/'), 3000);
      }
    })();
    return () => {
      closed = true;
      wsRef.current = null;
      ws?.close();
      lk?.disconnect();
    };
  }, [roomId]);

  function sendComment() {
    const content = input.trim();
    if (!content || !wsRef.current) return;
    wsRef.current.send(JSON.stringify({ type: 'comment.send', payload: { content } }));
    setInput('');
  }

  function like() {
    wsRef.current?.send(JSON.stringify({ type: 'like.send', payload: {} }));
  }

  async function follow() {
    if (!room) return;
    try {
      if (following) { await api.unfollow(room.anchorId); setFollowing(false); }
      else { await api.follow(room.anchorId); setFollowing(true); }
    } catch (e: any) { setErr(e.message); }
  }

  return (
    <div className="page">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>{room?.title || '直播间'} <span className="muted">在线 {online} · 赞 {likes}</span></h2>
        <div className="row">
          {room && (
            <button className={following ? 'gray' : ''} onClick={follow}>
              {following ? '已关注' : '关注主播'}
            </button>
          )}
          <button className="gray" onClick={() => nav('/')}>返回</button>
        </div>
      </div>
      {err && <div className="muted" style={{ color: '#f87171' }}>{err}</div>}
      <div ref={stageRef} className="card row" style={{ minHeight: 240, flexWrap: 'wrap' }} />
      <div className="card">
        <div className="comments">
          {comments.map(c => (
            <div className="comment" key={c.commentId}><b>{c.nickname}</b>：{c.content}</div>
          ))}
          {comments.length === 0 && <div className="muted">还没有评论，来抢沙发</div>}
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <input placeholder={muted ? '你已被禁言' : '说点什么…'} value={input}
                 onChange={e => setInput(e.target.value)}
                 onKeyDown={e => e.key === 'Enter' && sendComment()} />
          <button onClick={sendComment} disabled={muted || !input.trim()}>发送</button>
          <button onClick={like}>👍 点赞</button>
        </div>
      </div>
    </div>
  );
}
