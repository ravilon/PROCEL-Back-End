export type Role = "ADMIN" | "OPERADOR" | "ANALISTA" | "USUARIO" | "INGESTOR";

export interface Session {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  userId: string;
  email: string;
  roles: Role[];
}