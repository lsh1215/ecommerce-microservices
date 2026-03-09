export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  name: string;
}

export interface AuthUser {
  id: number;
  publicId: string;
  name: string;
  email: string;
}
