'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { AuthAPI } from '@/features/auth/api/auth-api';
import { cn } from '@/lib/utils';

const loginSchema = z.object({
  email: z.email('Enter a valid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

type LoginFormData = z.infer<typeof loginSchema>;

const inputClass = (hasError: boolean) =>
  cn(
    'w-full rounded-md border px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary',
    hasError ? 'border-destructive' : 'border-border',
  );

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get('redirect') ?? '/';
  const setAuth = useAuthStore((s) => s.setAuth);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const res = await AuthAPI.login({ email: data.email, password: data.password });
      if (!res.success || !res.data) {
        setError(res.error?.message ?? 'Invalid email or password');
        return;
      }
      setAuth({ id: res.data.id, name: res.data.name, email: res.data.email });
      router.push(redirectTo);
    } catch {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-xl border border-border bg-background p-8 shadow-sm">
      <h1 className="mb-1 text-2xl font-bold text-foreground">Welcome back</h1>
      <p className="mb-6 text-sm text-muted-foreground">Sign in to your account to continue</p>

      {error && (
        <div className="mb-4 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="email" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Email
          </label>
          <input
            id="email"
            type="email"
            {...register('email')}
            className={inputClass(!!errors.email)}
            placeholder="you@example.com"
          />
          {errors.email && (
            <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>
          )}
        </div>

        <div>
          <div className="mb-1 flex items-center justify-between">
            <label htmlFor="password" className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Password
            </label>
            <span className="text-xs text-muted-foreground">
              Forgot password?
            </span>
          </div>
          <input
            id="password"
            type="password"
            {...register('password')}
            className={inputClass(!!errors.password)}
            placeholder="Min. 8 characters"
          />
          {errors.password && (
            <p className="mt-1 text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
        >
          {isSubmitting ? (
            <>
              <Loader2 size={16} className="animate-spin" />
              Signing in...
            </>
          ) : (
            'Sign In'
          )}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Don&apos;t have an account?{' '}
        <Link href="/register" className="font-medium text-primary hover:underline underline-offset-4">
          Create one
        </Link>
      </p>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md rounded-xl border border-border p-8">
          <div className="animate-pulse space-y-4">
            <div className="h-8 rounded bg-muted" />
            <div className="h-48 rounded bg-muted" />
          </div>
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
