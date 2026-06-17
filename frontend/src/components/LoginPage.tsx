import { useState } from 'react';
import { KeyRound, LogIn, Mail, Sparkles, User, UserPlus } from 'lucide-react';
import { loginUser, registerUser } from '../api';

interface Props {
  onAuthSuccess: () => void;
  variant?: 'page' | 'modal';
  message?: string;
  onCancel?: () => void;
}

export default function LoginPage({ onAuthSuccess, variant = 'page', message, onCancel }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const isModal = variant === 'modal';

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
    <div className={(isModal ? '' : 'min-h-screen bg-gray-950 ') + 'flex items-center justify-center p-4'}>
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-xl bg-violet-600">
            <Sparkles size={28} className="text-white" />
          </div>
          <h1 className="text-xl font-bold tracking-tight text-white">LoreVista</h1>
          <p className="mt-1 text-sm text-gray-500">AI 小说 · 漫画工坊</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-gray-800 bg-gray-900 p-6">
          {message && (
            <p className="rounded-lg border border-violet-500/20 bg-violet-500/10 px-3 py-2 text-sm text-violet-200">
              {message}
            </p>
          )}

          <h2 className="text-sm font-semibold text-gray-200">{mode === 'login' ? '登录' : '注册'}</h2>

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
              className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 outline-none transition-colors focus:border-violet-500"
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
                className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 outline-none transition-colors focus:border-violet-500"
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
              className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-500 outline-none transition-colors focus:border-violet-500"
            />
          </div>

          {error && (
            <p className="rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-2 text-xs text-rose-400">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-violet-600 py-2.5 text-sm font-medium text-white transition-colors hover:bg-violet-500 disabled:opacity-50"
          >
            {loading ? (
              <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
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
            className="w-full text-center text-xs text-gray-600 transition-colors hover:text-violet-400"
          >
            {mode === 'login' ? '还没有账号？立即注册' : '已有账号？去登录'}
          </button>

          {isModal && onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="w-full text-center text-xs text-gray-500 transition-colors hover:text-gray-300"
            >
              稍后再说
            </button>
          )}
        </form>
      </div>
    </div>
  );
}
