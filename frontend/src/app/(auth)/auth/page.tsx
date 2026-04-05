'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { AuthAPI } from '@/features/auth/api/auth-api';
import { cn } from '@/lib/utils';

type AuthTab = 'login' | 'register';

const loginSchema = z.object({
  email: z.email('Enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

const registerSchema = z
  .object({
    name: z.string().min(2, 'Name must be at least 2 characters'),
    email: z.email('Enter a valid email'),
    password: z.string().min(6, 'Password must be at least 6 characters'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type LoginFormData = z.infer<typeof loginSchema>;
type RegisterFormData = z.infer<typeof registerSchema>;

const inputClass = (hasError: boolean) =>
  cn(
    'w-full rounded-md border px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary',
    hasError ? 'border-destructive' : 'border-border',
  );

function AuthForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') ?? '/';
  const setAuth = useAuthStore((s) => s.setAuth);

  const [tab, setTab] = useState<AuthTab>('login');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loginForm = useForm<LoginFormData>({ resolver: zodResolver(loginSchema) });
  const registerForm = useForm<RegisterFormData>({ resolver: zodResolver(registerSchema) });

  const handleLogin = async (data: LoginFormData) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const res = await AuthAPI.login({ email: data.email, password: data.password });
      if (!res.success || !res.data) {
        setError(res.error?.message ?? 'Invalid credentials');
        return;
      }
      setAuth({ id: res.data.id, name: res.data.name, email: res.data.email });
      router.push(redirect);
    } catch {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRegister = async (data: RegisterFormData) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const registerRes = await AuthAPI.register({
        email: data.email,
        password: data.password,
        name: data.name,
      });
      if (!registerRes.success) {
        setError(registerRes.error?.message ?? 'Registration failed');
        return;
      }
      const loginRes = await AuthAPI.login({ email: data.email, password: data.password });
      if (!loginRes.success || !loginRes.data) {
        setError('Account created. Please log in.');
        setTab('login');
        return;
      }
      setAuth({ id: loginRes.data.id, name: loginRes.data.name, email: loginRes.data.email });
      router.push(redirect);
    } catch {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-xl border border-border bg-background p-8 shadow-sm">
      <div className="flex border-b border-border">
        {(['login', 'register'] as AuthTab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => {
              setTab(t);
              setError(null);
            }}
            className={cn(
              'flex-1 py-3 text-sm font-semibold capitalize transition-colors',
              tab === t
                ? 'border-b-2 border-foreground text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {t === 'login' ? 'Login' : 'Register'}
          </button>
        ))}
      </div>

      {error && (
        <div className="mt-4 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {tab === 'login' && (
        <form onSubmit={loginForm.handleSubmit(handleLogin)} className="mt-6 space-y-4">
          <div>
            <label
              htmlFor="login-email"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Email
            </label>
            <input
              id="login-email"
              type="email"
              {...loginForm.register('email')}
              className={inputClass(!!loginForm.formState.errors.email)}
              placeholder="you@example.com"
            />
            {loginForm.formState.errors.email && (
              <p className="mt-1 text-xs text-destructive">
                {loginForm.formState.errors.email.message}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="login-password"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Password
            </label>
            <input
              id="login-password"
              type="password"
              {...loginForm.register('password')}
              className={inputClass(!!loginForm.formState.errors.password)}
              placeholder="Min. 6 characters"
            />
            {loginForm.formState.errors.password && (
              <p className="mt-1 text-xs text-destructive">
                {loginForm.formState.errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Logging in...
              </>
            ) : (
              'Login'
            )}
          </button>
        </form>
      )}

      {tab === 'register' && (
        <form onSubmit={registerForm.handleSubmit(handleRegister)} className="mt-6 space-y-4">
          <div>
            <label
              htmlFor="register-name"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Name
            </label>
            <input
              id="register-name"
              type="text"
              {...registerForm.register('name')}
              className={inputClass(!!registerForm.formState.errors.name)}
              placeholder="Your name"
            />
            {registerForm.formState.errors.name && (
              <p className="mt-1 text-xs text-destructive">
                {registerForm.formState.errors.name.message}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="register-email"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Email
            </label>
            <input
              id="register-email"
              type="email"
              {...registerForm.register('email')}
              className={inputClass(!!registerForm.formState.errors.email)}
              placeholder="you@example.com"
            />
            {registerForm.formState.errors.email && (
              <p className="mt-1 text-xs text-destructive">
                {registerForm.formState.errors.email.message}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="register-password"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Password
            </label>
            <input
              id="register-password"
              type="password"
              {...registerForm.register('password')}
              className={inputClass(!!registerForm.formState.errors.password)}
              placeholder="Min. 6 characters"
            />
            {registerForm.formState.errors.password && (
              <p className="mt-1 text-xs text-destructive">
                {registerForm.formState.errors.password.message}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="register-confirm"
              className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Confirm Password
            </label>
            <input
              id="register-confirm"
              type="password"
              {...registerForm.register('confirmPassword')}
              className={inputClass(!!registerForm.formState.errors.confirmPassword)}
              placeholder="Re-enter password"
            />
            {registerForm.formState.errors.confirmPassword && (
              <p className="mt-1 text-xs text-destructive">
                {registerForm.formState.errors.confirmPassword.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Creating account...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>
      )}
    </div>
  );
}

export default function AuthPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md rounded-xl border border-border p-8">
          <div className="animate-pulse space-y-4">
            <div className="h-10 rounded bg-muted" />
            <div className="h-48 rounded bg-muted" />
          </div>
        </div>
      }
    >
      <AuthForm />
    </Suspense>
  );
}
