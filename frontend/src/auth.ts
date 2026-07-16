const AUTH_KEY = "trip_auth";

export function isAuthed() {
  return localStorage.getItem(AUTH_KEY) === "true";
}

export function signIn() {
  localStorage.setItem(AUTH_KEY, "true");
}

export function signOut() {
  localStorage.removeItem(AUTH_KEY);
}
