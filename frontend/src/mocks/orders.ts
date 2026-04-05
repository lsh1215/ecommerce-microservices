import type { Order } from '@/types';

export const mockOrders: Order[] = [
  {
    id: 'order-001',
    orderNumber: 'ORD-20250101-001',
    status: 'CONFIRMED',
    items: [
      {
        productId: 'prod-001',
        variantId: 'v-001-m',
        productName: 'Classic Oxford Shirt',
        brandName: 'Nordic Basic',
        size: 'M',
        color: 'White',
        price: 49000,
        imageUrl: 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=400&q=80',
        quantity: 1,
        totalPrice: 49000,
      },
    ],
    shippingAddress: {
      recipientName: 'Kim Minsu',
      phone: '010-1234-5678',
      zipCode: '06234',
      address1: '서울특별시 강남구 강남대로 123',
    },
    totalAmount: 49000,
    createdAt: '2025-01-01T09:00:00Z',
    updatedAt: '2025-01-01T09:00:00Z',
  },
  {
    id: 'order-002',
    orderNumber: 'ORD-20250105-002',
    status: 'DELIVERED',
    items: [
      {
        productId: 'prod-003',
        variantId: 'v-003-l',
        productName: 'Wool Blend Overcoat',
        brandName: 'Urban Thread',
        size: 'L',
        color: 'Camel',
        price: 189000,
        imageUrl: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=400&q=80',
        quantity: 1,
        totalPrice: 189000,
      },
      {
        productId: 'prod-002',
        variantId: 'v-002-32',
        productName: 'Slim Chino Trousers',
        brandName: 'Nordic Basic',
        size: '32',
        color: 'Khaki',
        price: 69000,
        imageUrl: 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=400&q=80',
        quantity: 1,
        totalPrice: 69000,
      },
    ],
    shippingAddress: {
      recipientName: 'Kim Minsu',
      phone: '010-1234-5678',
      zipCode: '06234',
      address1: '서울특별시 강남구 강남대로 123',
    },
    totalAmount: 258000,
    createdAt: '2025-01-05T14:30:00Z',
    updatedAt: '2025-01-12T10:00:00Z',
  },
  {
    id: 'order-003',
    orderNumber: 'ORD-20250110-003',
    status: 'SHIPPING',
    items: [
      {
        productId: 'prod-005',
        variantId: 'v-005-m',
        productName: 'Denim Jacket',
        brandName: 'Urban Thread',
        size: 'M',
        color: 'Indigo',
        price: 99000,
        imageUrl: 'https://images.unsplash.com/photo-1601333144130-8cbb312386b6?w=400&q=80',
        quantity: 1,
        totalPrice: 99000,
      },
    ],
    shippingAddress: {
      recipientName: 'Kim Minsu',
      phone: '010-1234-5678',
      zipCode: '06234',
      address1: '서울특별시 강남구 강남대로 123',
    },
    totalAmount: 99000,
    createdAt: '2025-01-10T11:00:00Z',
    updatedAt: '2025-01-11T09:00:00Z',
  },
];

export const mockOrder = mockOrders[0]!;

export function getOrderById(id: string): Order | undefined {
  return mockOrders.find((o) => o.id === id);
}
