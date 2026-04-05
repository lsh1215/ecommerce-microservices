'use client';

import { useState } from 'react';
import { redirect } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { Skeleton } from '@/components/shared/Skeleton';
import { cn } from '@/lib/utils';
import { CustomerAPI } from '@/features/auth/api/auth-api';

const profileSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters'),
  phone: z
    .string()
    .regex(/^010-\d{4}-\d{4}$/, 'Phone must be in format 010-XXXX-XXXX')
    .optional()
    .or(z.literal('')),
});

type ProfileFormData = z.infer<typeof profileSchema>;

const inputClass = (hasError: boolean, disabled?: boolean) =>
  cn(
    'w-full rounded-md border px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary',
    hasError ? 'border-destructive' : 'border-border',
    disabled && 'bg-muted text-muted-foreground cursor-not-allowed',
  );

export default function ProfilePage() {
  const user = useFromStore(useAuthStore, (s) => s.user);
  const setAuth = useAuthStore((s) => s.setAuth);

  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
  });

  if (user === undefined) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Profile</h1>
        <div className="space-y-6">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/account/profile');
  }

  const handleEdit = () => {
    reset({ name: user.name, phone: '' });
    setSaveError(null);
    setSaveSuccess(false);
    setIsEditing(true);
  };

  const handleCancel = () => {
    setIsEditing(false);
    setSaveError(null);
  };

  const onSubmit = async (data: ProfileFormData) => {
    setIsSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const res = await CustomerAPI.updateProfile(user.id, {
        name: data.name,
        phone: data.phone || undefined,
      });
      if (!res.success || !res.data) {
        setSaveError(res.error?.message ?? 'Failed to update profile.');
        return;
      }
      setAuth({ ...user, name: res.data.name, email: res.data.email });
      setSaveSuccess(true);
      setIsEditing(false);
    } catch {
      setSaveError('Failed to update profile. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-3xl font-bold text-foreground">Profile</h1>
        {!isEditing && (
          <button
            type="button"
            onClick={handleEdit}
            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted"
          >
            Edit Profile
          </button>
        )}
      </div>

      {saveSuccess && (
        <div className="mb-6 rounded-md border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
          Profile updated successfully.
        </div>
      )}

      {saveError && (
        <div className="mb-6 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {saveError}
        </div>
      )}

      <section>
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Account Information
        </h2>

        {isEditing ? (
          <form onSubmit={handleSubmit(onSubmit)} className="rounded-lg border border-border p-5">
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
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
                  defaultValue={user.name}
                  className={inputClass(!!errors.name)}
                />
                {errors.name && (
                  <p className="mt-1 text-xs text-destructive">{errors.name.message}</p>
                )}
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
                  value={user.email}
                  disabled
                  className={inputClass(false, true)}
                />
                <p className="mt-1 text-xs text-muted-foreground">Email cannot be changed</p>
              </div>

              <div>
                <label
                  htmlFor="phone"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Phone <span className="font-normal normal-case">(optional)</span>
                </label>
                <input
                  id="phone"
                  type="tel"
                  {...register('phone')}
                  className={inputClass(!!errors.phone)}
                  placeholder="010-0000-0000"
                />
                {errors.phone && (
                  <p className="mt-1 text-xs text-destructive">{errors.phone.message}</p>
                )}
              </div>
            </div>

            <div className="mt-6 flex gap-3">
              <button
                type="submit"
                disabled={isSaving}
                className="flex items-center gap-2 rounded-md bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
              >
                {isSaving ? (
                  <>
                    <Loader2 size={14} className="animate-spin" />
                    Saving...
                  </>
                ) : (
                  'Save Changes'
                )}
              </button>
              <button
                type="button"
                disabled={isSaving}
                onClick={handleCancel}
                className="rounded-md border border-border px-5 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-60"
              >
                Cancel
              </button>
            </div>
          </form>
        ) : (
          <div className="rounded-lg border border-border p-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <p className="text-xs font-medium text-muted-foreground">Name</p>
                <p className="mt-1 text-sm font-medium text-foreground">{user.name}</p>
              </div>
              <div>
                <p className="text-xs font-medium text-muted-foreground">Email</p>
                <p className="mt-1 text-sm font-medium text-foreground">{user.email}</p>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
