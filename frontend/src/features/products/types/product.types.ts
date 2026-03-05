export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  categoryId: string;
  categoryName: string;
  imageUrl?: string;
  stockQuantity: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductListParams {
  page?: string;
  size?: string;
  keyword?: string;
  categoryId?: string;
}
