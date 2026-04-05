export interface ProductListParams {
  keyword?: string;
  brandId?: string | number;
  category?: string;
  minPrice?: string | number;
  maxPrice?: string | number;
  page?: string | number;
  size?: string | number;
  sort?: string;
}
