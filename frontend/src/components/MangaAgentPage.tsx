import { useState } from 'react';
import { Bot, Loader2, Send } from 'lucide-react';
import { runMangaAgent } from '../api';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

export default function MangaAgentPage() {
  const [chapterId, setChapterId] = useState('');
  const [input, setInput] = useState('帮我检查当前章节的漫画生成进度，并告诉我下一步做什么');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const send = async () => {
    const id = Number(chapterId);
    const text = input.trim();
    if (!id || !text || loading) return;
    setLoading(true);
    setError('');
    setMessages((prev) => [...prev, { role: 'user', content: text }]);
    setInput('');
    try {
      const result = await runMangaAgent(id, text);
      setMessages((prev) => [...prev, { role: 'assistant', content: result.reply }]);
    } catch (err: any) {
      setError(err.message || '请求失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-dvh w-screen flex-col bg-gray-950 text-gray-100">
      <header className="flex h-14 shrink-0 items-center gap-3 border-b border-gray-800 px-5">
        <Bot size={18} className="text-violet-300" />
        <div>
          <h1 className="text-sm font-semibold">漫画智能体</h1>
          <p className="text-xs text-gray-500">内部调试入口，使用 AgentScope 工具链协助漫画流程</p>
        </div>
      </header>

      <main className="mx-auto flex min-h-0 w-full max-w-5xl flex-1 flex-col gap-4 p-4">
        <div className="flex shrink-0 gap-2">
          <input
            value={chapterId}
            onChange={(e) => setChapterId(e.target.value.replace(/\D/g, ''))}
            className="w-40 rounded-lg border border-gray-800 bg-gray-900 px-3 py-2 text-sm outline-none focus:border-violet-500"
            placeholder="Chapter ID"
          />
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') send();
            }}
            className="min-w-0 flex-1 rounded-lg border border-gray-800 bg-gray-900 px-3 py-2 text-sm outline-none focus:border-violet-500"
            placeholder="告诉智能体你想完成什么"
          />
          <button
            onClick={send}
            disabled={loading || !chapterId || !input.trim()}
            className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-500 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {loading ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            发送
          </button>
        </div>

        {error && <div className="rounded-lg border border-red-500/30 bg-red-950/40 px-3 py-2 text-sm text-red-200">{error}</div>}

        <div className="min-h-0 flex-1 overflow-y-auto rounded-lg border border-gray-800 bg-gray-900/40 p-4">
          {messages.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-gray-500">
              输入章节 ID 后，可以让智能体检查上下文、生成分镜、整理下一步。
            </div>
          ) : (
            <div className="space-y-3">
              {messages.map((msg, idx) => (
                <div key={idx} className={msg.role === 'user' ? 'text-right' : 'text-left'}>
                  <div
                    className={
                      'inline-block max-w-[78%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm leading-relaxed ' +
                      (msg.role === 'user'
                        ? 'bg-violet-600 text-white'
                        : 'border border-gray-800 bg-gray-950 text-gray-200')
                    }
                  >
                    {msg.content}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
