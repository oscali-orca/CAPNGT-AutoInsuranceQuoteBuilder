const API = "http://localhost:8080";

interface ErrorBody {
  message?: string;
  detail?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  status: number;
  retryable: boolean;
  fieldErrors: Record<string, string>;

  constructor(status: number, body?: ErrorBody | null) {
    const fallback = status === 401 ? "Please sign in to continue."
      : status === 403 ? "Request denied. Sign in again and retry."
      : `Request failed (${status})`;
    super(body?.message || body?.detail || fallback);
    this.status = status;
    this.retryable = status === 0 || status >= 500;
    this.fieldErrors = body?.fieldErrors ?? {};
  }
}

export async function apiRequest<T>(path: string, options: {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
} = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {};
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const csrf = await apiRequest<{ token: string }>("/api/csrf", { signal: options.signal });
    headers["X-XSRF-TOKEN"] = csrf.token;
  }

  let response: Response;
  try {
    response = await fetch(`${API}${path}`, {
      method,
      credentials: "include",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(0, { message: "Cannot reach the backend. Check that it is running on localhost:8080." });
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, data);
  if (data === null && response.status !== 204) {
    throw new ApiError(502, { message: "The backend returned an invalid response." });
  }
  return data as T;
}

export function signIn(username: string, password: string) {
  return apiRequest<{ username: string; message: string }>("/api/session", {
    method: "POST", body: { username: username.trim(), password },
  });
}

export function signOut() {
  return apiRequest<{ message: string }>("/api/session", { method: "DELETE" });
}

export function getCurrentSession() {
  return apiRequest<{ display_name: string }>("/api/session");
}

export function getQuoteConfig() {
  return apiRequest<{
    versionCode: string;
    description: string;
    currency: string;
    config: Record<string, unknown>;
  }>("/api/quote-config");
}
