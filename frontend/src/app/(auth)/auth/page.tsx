'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store/auth-store';

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

function AuthForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') ?? '/';
  const setAuth = useAuthStore((s) => s.setAuth);

  const [tab, setTab] = useState<AuthTab>('login');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loginForm = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const registerForm = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const handleLogin = async (data: LoginFormData) => {
    setIsSubmitting(true);
    setError(null);
    await new Promise((resolve) => setTimeout(resolve, 800));

    setAuth(
      { id: 'user-001', email: data.email, name: data.email.split('@')[0] ?? 'User' },
      'mock-jwt-token',
    );
    router.push(redirect);
  };

  const handleRegister = async (data: RegisterFormData) => {
    setIsSubmitting(true);
    setError(null);
    await new Promise((resolve) => setTimeout(resolve, 800));

    setAuth(
      { id: 'user-001', email: data.email, name: data.name },
      'mock-jwt-token',
    );
    router.push(redirect);
  };

  const inputClass = (hasError: boolean) =>
    `w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
      hasError ? 'border-red-400 focus:border-red-500' : 'border-[#e8e4df] focus:border-[#1a1a1a]'
    }`;

  return (
    <div className="w-full max-w-md">
      {/* Tab toggle */}
      <div className="flex border-b border-[#e8e4df]">
        <button
          type="button"
          onClick={() => {
            setTab('login');
            setError(null);
          }}
          className={`flex-1 py-3 text-sm font-semibold uppercase tracking-widest transition-colors ${
            tab === 'login'
              ? 'border-b-2 border-[#1a1a1a] text-[#1a1a1a]'
              : 'text-[#6b6560] hover:text-[#1a1a1a]'
          }`}
        >
          Login
        </button>
        <button
          type="button"
          onClick={() => {
            setTab('register');
            setError(null);
          }}
          className={`flex-1 py-3 text-sm font-semibold uppercase tracking-widest transition-colors ${
            tab === 'register'
              ? 'border-b-2 border-[#1a1a1a] text-[#1a1a1a]'
              : 'text-[#6b6560] hover:text-[#1a1a1a]'
          }`}
        >
          Register
        </button>
      </div>

      {error && (
        <div className="mt-4 border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {/* Login form */}
      {tab === 'login' && (
        <form onSubmit={loginForm.handleSubmit(handleLogin)} className="mt-6 space-y-4">
          <div>
            <label htmlFor="login-email" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{loginForm.formState.errors.email.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="login-password" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{loginForm.formState.errors.password.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 bg-[#c4633e] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e] disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Logging in...
              </>
            ) : (
              'Login'
            )}
          </button>
        </form>
      )}

      {/* Register form */}
      {tab === 'register' && (
        <form onSubmit={registerForm.handleSubmit(handleRegister)} className="mt-6 space-y-4">
          <div>
            <label htmlFor="register-name" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{registerForm.formState.errors.name.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="register-email" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{registerForm.formState.errors.email.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="register-password" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{registerForm.formState.errors.password.message}</p>
            )}
          </div>

          <div>
            <label htmlFor="register-confirm" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
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
              <p className="mt-1 text-xs text-red-500">{registerForm.formState.errors.confirmPassword.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 bg-[#c4633e] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e] disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Creating account...
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
        <div className="w-full max-w-md">
          <div className="animate-pulse space-y-4">
            <div className="h-10 bg-[#e8e4df]" />
            <div className="h-48 bg-[#e8e4df]" />
          </div>
        </div>
      }
    >
      <AuthForm />
    </Suspense>
  );
}
