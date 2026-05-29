import { useState } from 'react';
import { Sparkles, User, Mail, KeyRound, LogIn, UserPlus } from 'lucide-react';
import { loginUser, registerUser } from '../api';

interface Props {
  onAuthSuccess: () => void;
}

export default function LoginPage({ onAuthSuccess }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password.trim()) {
      setError('请填写用户名和密码');
      return;
    }
    if (mode === 'register' && !email.trim()) {
      setError('请填写邮箱');
      return;
    }
    setLoading(true);
    try {
      if (mode === 'login') {
        await loginUser(username.trim(), password);
      } else {
        await registerUser(username.trim(), email.trim(), password);
      }
      onAuthSuccess();
    } catch (err: any) {
      setError(err.message || '操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-violet-600 mb-4">
            <Sparkles size={28} className="text-white" />
          </div>
          <h1 className="text-xl font-bold text-white tracking-tight">LoreVista</h1>
          <p className="text-sm text-gray-500 mt-1">AI 小说 · 漫画工坊</p>
        </div>

        <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-4">
          <h2 className="text-sm font-semibold text-gray-200">
            {mode === 'login' ? '登录' : '注册'}
          </h2>

          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-xs text-gray-500">
              <User size={12} />用户名
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="输入用户名"
              autoFocus
              className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-100
                         placeholder-gray-500 outline-none focus:border-violet-500 transition-colors"
            />
          </div>

          {mode === 'register' && (
            <div className="space-y-1.5">
              <label className="flex items-center gap-1.5 text-xs text-gray-500">
                <Mail size={12} />邮箱
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="输入邮箱地址"
                className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-100
                           placeholder-gray-500 outline-none focus:border-violet-500 transition-colors"
              />
            </div>
          )}

          <div className="space-y-1.5">
            <label className="flex items-center gap-1.5 text-xs text-gray-500">
              <KeyRound size={12} />密码
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="输入密码"
              className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-100
                         placeholder-gray-500 outline-none focus:border-violet-500 transition-colors"
            />
          </div>

          {error && (
            <p className="text-xs text-rose-400 bg-rose-500/10 border border-rose-500/20 rounded-lg px-3 py-2">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 py-2.5 bg-violet-600 hover:bg-violet-500
                       text-white text-sm font-medium rounded-lg transition-colors disabled:opacity-50"
          >
            {loading ? (
              <span className="inline-block w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : mode === 'login' ? (
              <LogIn size={16} />
            ) : (
              <UserPlus size={16} />
            )}
            {mode === 'login' ? '登录' : '注册'}
          </button>

          <button
            type="button"
            onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}
            className="w-full text-center text-xs text-gray-600 hover:text-violet-400 transition-colors"
          >
            {mode === 'login' ? '还没有账号？立即注册' : '已有账号？去登录'}
          </button>
        </form>
      </div>
    </div>
  );
}
