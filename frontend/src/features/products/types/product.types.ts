export interface ProductListParams {
  brandId?: string;
  category?: string;
  era?: string;
  fabricType?: string;
  fabricWeave?: string;
  minPrice?: string;
  maxPrice?: string;
  page?: string;
  size?: string;
  sort?: string;
  direction?: string;
}

export interface ProductSearchParams {
  q: string;
  page?: number;
  size?: number;
}
