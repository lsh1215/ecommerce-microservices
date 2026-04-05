/**
 * Service base URLs for backend v3 MSA.
 * Each service is deployed independently and has its own port in local dev.
 * In Kubernetes these can be routed through a single ingress by overriding
 * NEXT_PUBLIC_API_BASE_URL for all four.
 */
const PRODUCT_API_URL =
  process.env.NEXT_PUBLIC_PRODUCT_API_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://localhost:8081';

const ORDER_API_URL =
  process.env.NEXT_PUBLIC_ORDER_API_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://localhost:8082';

const PAYMENT_API_URL =
  process.env.NEXT_PUBLIC_PAYMENT_API_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://localhost:8083';

const CUSTOMER_API_URL =
  process.env.NEXT_PUBLIC_CUSTOMER_API_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://localhost:8084';

export type ServiceName = 'product' | 'order' | 'payment' | 'customer';

export const SERVICE_BASE_URLS: Record<ServiceName, string> = {
  product: PRODUCT_API_URL,
  order: ORDER_API_URL,
  payment: PAYMENT_API_URL,
  customer: CUSTOMER_API_URL,
};

/** @deprecated kept for any remaining legacy callers; prefer SERVICE_BASE_URLS */
export const API_BASE_URL = PRODUCT_API_URL;
