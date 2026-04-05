'use client';

import { useEffect, useState } from 'react';
import { redirect } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, MapPin, Plus, Trash2, Pencil } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { Skeleton } from '@/components/shared/Skeleton';
import { cn } from '@/lib/utils';
import { AddressAPI } from '@/features/addresses/api/address-api';
import type { AddressResponse, AddressLabel } from '@/types/api-responses';

const addressSchema = z.object({
  label: z.enum(['HOME', 'WORK', 'OTHER']),
  recipientName: z.string().min(2, 'Recipient name is required'),
  phone: z.string().regex(/^010-\d{4}-\d{4}$/, 'Phone must be in format 010-XXXX-XXXX'),
  zipCode: z.string().min(5, 'Zip code is required'),
  address1: z.string().min(5, 'Address is required'),
  address2: z.string().optional(),
});

type AddressFormData = z.infer<typeof addressSchema>;

const LABEL_DISPLAY: Record<AddressLabel, string> = {
  HOME: 'Home',
  WORK: 'Work',
  OTHER: 'Other',
};

const inputClass = (hasError: boolean) =>
  cn(
    'w-full rounded-md border px-3 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary',
    hasError ? 'border-destructive' : 'border-border',
  );

interface AddressFormProps {
  defaultValues?: Partial<AddressFormData>;
  onSubmit: (data: AddressFormData) => Promise<void>;
  onCancel: () => void;
  isSubmitting: boolean;
  submitLabel: string;
}

function AddressForm({
  defaultValues,
  onSubmit,
  onCancel,
  isSubmitting,
  submitLabel,
}: AddressFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AddressFormData>({
    resolver: zodResolver(addressSchema),
    defaultValues,
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-4 rounded-lg border border-border p-5"
    >
      <div>
        <label
          htmlFor="label"
          className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          Label
        </label>
        <select
          id="label"
          {...register('label')}
          className={cn(inputClass(!!errors.label), 'bg-background')}
        >
          <option value="HOME">Home</option>
          <option value="WORK">Work</option>
          <option value="OTHER">Other</option>
        </select>
        {errors.label && <p className="mt-1 text-xs text-destructive">{errors.label.message}</p>}
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="recipientName"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Recipient Name
          </label>
          <input
            id="recipientName"
            type="text"
            {...register('recipientName')}
            className={inputClass(!!errors.recipientName)}
            placeholder="Full name"
          />
          {errors.recipientName && (
            <p className="mt-1 text-xs text-destructive">{errors.recipientName.message}</p>
          )}
        </div>

        <div>
          <label
            htmlFor="phone"
            className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
          >
            Phone
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
      </div>

      <div>
        <label
          htmlFor="zipCode"
          className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          Zip Code
        </label>
        <input
          id="zipCode"
          type="text"
          {...register('zipCode')}
          className={cn(inputClass(!!errors.zipCode), 'max-w-xs')}
          placeholder="12345"
        />
        {errors.zipCode && (
          <p className="mt-1 text-xs text-destructive">{errors.zipCode.message}</p>
        )}
      </div>

      <div>
        <label
          htmlFor="address1"
          className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          Address
        </label>
        <input
          id="address1"
          type="text"
          {...register('address1')}
          className={inputClass(!!errors.address1)}
          placeholder="Street address"
        />
        {errors.address1 && (
          <p className="mt-1 text-xs text-destructive">{errors.address1.message}</p>
        )}
      </div>

      <div>
        <label
          htmlFor="address2"
          className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground"
        >
          Address Line 2 <span className="font-normal normal-case">(optional)</span>
        </label>
        <input
          id="address2"
          type="text"
          {...register('address2')}
          className={inputClass(false)}
          placeholder="Apartment, suite, etc."
        />
      </div>

      <div className="flex gap-3 pt-1">
        <button
          type="submit"
          disabled={isSubmitting}
          className="flex items-center gap-2 rounded-md bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
        >
          {isSubmitting ? (
            <>
              <Loader2 size={14} className="animate-spin" />
              Saving...
            </>
          ) : (
            submitLabel
          )}
        </button>
        <button
          type="button"
          disabled={isSubmitting}
          onClick={onCancel}
          className="rounded-md border border-border px-5 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-60"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

export default function AddressesPage() {
  const user = useFromStore(useAuthStore, (s) => s.user);
  const [addresses, setAddresses] = useState<AddressResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    (async () => {
      setIsLoading(true);
      setLoadError(null);
      const res = await AddressAPI.list(user.id);
      if (cancelled) return;
      if (res.success && res.data) {
        setAddresses(res.data);
      } else {
        setLoadError(res.error?.message ?? 'Failed to load addresses.');
      }
      setIsLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [user]);

  if (user === undefined) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Addresses</h1>
        <div className="space-y-4">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/account/addresses');
  }

  const handleAdd = async (data: AddressFormData) => {
    setIsSubmitting(true);
    try {
      const res = await AddressAPI.create(user.id, {
        ...data,
        address2: data.address2 || undefined,
        isDefault: addresses.length === 0,
      });
      if (res.success && res.data) {
        setAddresses((prev) => [...prev, res.data as AddressResponse]);
        setShowAddForm(false);
      } else {
        setLoadError(res.error?.message ?? 'Failed to add address.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEdit = async (data: AddressFormData) => {
    if (editingId == null) return;
    setIsSubmitting(true);
    try {
      const res = await AddressAPI.update(user.id, editingId, {
        ...data,
        address2: data.address2 || undefined,
      });
      if (res.success && res.data) {
        const updated = res.data;
        setAddresses((prev) => prev.map((a) => (a.id === editingId ? updated : a)));
        setEditingId(null);
      } else {
        setLoadError(res.error?.message ?? 'Failed to update address.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    const res = await AddressAPI.remove(user.id, id);
    if (res.success) {
      setAddresses((prev) => prev.filter((a) => a.id !== id));
    } else {
      setLoadError(res.error?.message ?? 'Failed to delete address.');
    }
  };

  const handleSetDefault = async (id: number) => {
    const address = addresses.find((a) => a.id === id);
    if (!address) return;
    const res = await AddressAPI.update(user.id, id, {
      label: address.label,
      recipientName: address.recipientName,
      phone: address.phone,
      zipCode: address.zipCode,
      address1: address.address1,
      address2: address.address2,
      isDefault: true,
    });
    if (res.success) {
      setAddresses((prev) => prev.map((a) => ({ ...a, isDefault: a.id === id })));
    } else {
      setLoadError(res.error?.message ?? 'Failed to set default address.');
    }
  };

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-3xl font-bold text-foreground">Addresses</h1>
        {!showAddForm && editingId == null && (
          <button
            type="button"
            onClick={() => setShowAddForm(true)}
            className="flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            <Plus size={14} />
            Add Address
          </button>
        )}
      </div>

      {loadError && (
        <div className="mb-6 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {loadError}
        </div>
      )}

      {showAddForm && (
        <div className="mb-6">
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            New Address
          </h2>
          <AddressForm
            onSubmit={handleAdd}
            onCancel={() => setShowAddForm(false)}
            isSubmitting={isSubmitting}
            submitLabel="Add Address"
          />
        </div>
      )}

      {isLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      ) : addresses.length === 0 && !showAddForm ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border py-16 text-center">
          <MapPin size={36} className="mb-3 text-muted-foreground" strokeWidth={1.5} />
          <p className="text-sm font-medium text-foreground">No saved addresses</p>
          <p className="mt-1 text-xs text-muted-foreground">Add an address to speed up checkout</p>
          <button
            type="button"
            onClick={() => setShowAddForm(true)}
            className="mt-4 flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            <Plus size={14} />
            Add Address
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {addresses.map((address) => (
            <div key={address.id}>
              {editingId === address.id ? (
                <div>
                  <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Edit Address
                  </h2>
                  <AddressForm
                    defaultValues={{
                      label: address.label,
                      recipientName: address.recipientName,
                      phone: address.phone,
                      zipCode: address.zipCode,
                      address1: address.address1,
                      address2: address.address2,
                    }}
                    onSubmit={handleEdit}
                    onCancel={() => setEditingId(null)}
                    isSubmitting={isSubmitting}
                    submitLabel="Save Changes"
                  />
                </div>
              ) : (
                <div className="rounded-lg border border-border p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className="rounded-full border border-border px-2 py-0.5 text-xs font-medium text-foreground">
                          {LABEL_DISPLAY[address.label]}
                        </span>
                        {address.isDefault && (
                          <span className="rounded-full bg-primary-light px-2 py-0.5 text-xs font-medium text-primary">
                            Default
                          </span>
                        )}
                      </div>
                      <p className="text-sm font-medium text-foreground">{address.recipientName}</p>
                      <p className="mt-0.5 text-sm text-muted-foreground">{address.phone}</p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        ({address.zipCode}) {address.address1}
                        {address.address2 && `, ${address.address2}`}
                      </p>
                    </div>

                    <div className="flex shrink-0 items-center gap-1">
                      {!address.isDefault && (
                        <button
                          type="button"
                          onClick={() => handleSetDefault(address.id)}
                          className="rounded-md px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                        >
                          Set default
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(address.id);
                          setShowAddForm(false);
                        }}
                        className="flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                        aria-label="Edit address"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(address.id)}
                        className="flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
                        aria-label="Delete address"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
