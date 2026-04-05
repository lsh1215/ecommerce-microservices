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

const registerSchema = z
  .object({
    name: z.string().min(2, 'Name must be at least 2 characters'),
    email: z.email('Enter a valid email'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string(),
    phone: z
      .string()
      .regex(/^010-\d{4}-\d{4}$/, 'Phone must be in format 010-XXXX-XXXX')
      .optional()
      .or(z.literal('')),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

const inputClass = (hasError: boolean) =>
  cn(
    'w-full rounded-md border px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary',
    hasError ? 'border-destructive' : 'border-border',
  );

function getPasswordStrength(password: string): { level: number; label: string; color: string } {
  if (password.length === 0) return { level: 0, label: '', color: '' };
  if (password.length < 8) return { level: 1, label: 'Weak', color: 'bg-destructive' };

  let score = 0;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  if (password.length >= 12) score++;

  if (score <= 1) return { level: 2, label: 'Fair', color: 'bg-warning' };
  if (score <= 2) return { level: 3, label: 'Good', color: 'bg-blue-500' };
  return { level: 4, label: 'Strong', color: 'bg-success' };
}

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get('redirect') ?? '/';
  const setAuth = useAuthStore((s) => s.setAuth);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [watchedPassword, setWatchedPassword] = useState('');

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const strength = getPasswordStrength(watchedPassword);

  const onSubmit = async (data: RegisterFormData) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const registerRes = await AuthAPI.register({
        email: data.email,
        password: data.password,
        name: data.name,
        phone: data.phone || undefined,
      });

      if (!registerRes.success) {
        setError(registerRes.error?.message ?? 'Registration failed. Please try again.');
        return;
      }

      const loginRes = await AuthAPI.login({ email: data.email, password: data.password });
      if (!loginRes.success || !loginRes.data) {
        router.push('/login');
        return;
      }

      setAuth({ id: loginRes.data.id, name: loginRes.data.name, email: loginRes.data.email });
      router.push(redirectTo);
    } catch {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md rounded-xl border border-border bg-background p-8 shadow-sm">
      <h1 className="mb-1 text-2xl font-bold text-foreground">Create account</h1>
      <p className="mb-6 text-sm text-muted-foreground">Fill in your details to get started</p>

      {error && (
        <div className="mb-4 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label
            htmlFor="name"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Full Name
          </label>
          <input
            id="name"
            type="text"
            {...register('name')}
            className={inputClass(!!errors.name)}
            placeholder="Your full name"
          />
          {errors.name && <p className="mt-1 text-xs text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <label
            htmlFor="email"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Email
          </label>
          <input
            id="email"
            type="email"
            {...register('email')}
            className={inputClass(!!errors.email)}
            placeholder="you@example.com"
          />
          {errors.email && <p className="mt-1 text-xs text-destructive">{errors.email.message}</p>}
        </div>

        <div>
          <label
            htmlFor="phone"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Phone <span className="font-normal normal-case text-muted-foreground">(optional)</span>
          </label>
          <input
            id="phone"
            type="tel"
            {...register('phone')}
            className={inputClass(!!errors.phone)}
            placeholder="010-0000-0000"
          />
          {errors.phone && <p className="mt-1 text-xs text-destructive">{errors.phone.message}</p>}
        </div>

        <div>
          <label
            htmlFor="password"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Password
          </label>
          <input
            id="password"
            type="password"
            {...register('password', {
              onChange: (e) => setWatchedPassword(e.target.value as string),
            })}
            className={inputClass(!!errors.password)}
            placeholder="Min. 8 characters"
          />
          {watchedPassword.length > 0 && (
            <div className="mt-2">
              <div className="flex gap-1">
                {[1, 2, 3, 4].map((level) => (
                  <div
                    key={level}
                    className={cn(
                      'h-1 flex-1 rounded-full transition-colors',
                      level <= strength.level ? strength.color : 'bg-muted',
                    )}
                  />
                ))}
              </div>
              {strength.label && (
                <p className="mt-1 text-xs text-muted-foreground">
                  Password strength:{' '}
                  <span className="font-medium text-foreground">{strength.label}</span>
                </p>
              )}
            </div>
          )}
          {errors.password && (
            <p className="mt-1 text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>

        <div>
          <label
            htmlFor="confirmPassword"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Confirm Password
          </label>
          <input
            id="confirmPassword"
            type="password"
            {...register('confirmPassword')}
            className={inputClass(!!errors.confirmPassword)}
            placeholder="Re-enter your password"
          />
          {errors.confirmPassword && (
            <p className="mt-1 text-xs text-destructive">{errors.confirmPassword.message}</p>
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
              Creating account...
            </>
          ) : (
            'Create Account'
          )}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-muted-foreground">
        Already have an account?{' '}
        <Link href="/login" className="font-medium text-primary hover:underline underline-offset-4">
          Sign in
        </Link>
      </p>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-md rounded-xl border border-border p-8">
          <div className="animate-pulse space-y-4">
            <div className="h-8 rounded bg-muted" />
            <div className="h-72 rounded bg-muted" />
          </div>
        </div>
      }
    >
      <RegisterForm />
    </Suspense>
  );
}
