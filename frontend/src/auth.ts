export type AuthUser = {
  id: number;
  name: string;
  email: string;
  phone?: string;
};

const TOKEN_KEY = "trip_token";
const USER_KEY = "trip_user";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getCurrentUser(): AuthUser | null {
  const value = localStorage.getItem(USER_KEY);
  if (!value) return null;
  try {
    return JSON.parse(value) as AuthUser;
  } catch {
    return null;
  }
}

export function isAuthed() {
  const token = getToken();
  if (!token) return false;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) throw new Error("Invalid JWT");
    const encoded = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(encoded.padEnd(encoded.length + ((4 - encoded.length % 4) % 4), "="))) as {
      exp?: number;
    };
    if (!payload.exp || payload.exp * 1000 <= Date.now()) {
      signOut();
      return false;
    }
    return true;
  } catch {
    signOut();
    return false;
  }
}

export function signIn(token: string, user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function signOut() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
