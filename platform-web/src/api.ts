export interface Identity {
  id: number;
  username: string;
  displayName: string;
  role: "ADMIN" | "USER";
}
export interface Session {
  id: string;
  title: string;
  mode: "business" | "knowledge";
  updatedAt: number;
}
export interface Citation {
  source: string;
  section: string;
  version: string;
  chunkId?: string;
}
export interface TraceStep {
  id: string;
  kind: string;
  label: string;
  status: string;
  offsetMs: number;
  durationMs: number;
  summary?: string;
}
export interface GatewayCall {
  requestId: string;
  traceId: string;
  appId: string;
  model: string;
  kind: string;
  createdAt: number;
  durationMs: number;
  firstTokenMs: number | null;
  status: string;
  attempts: number;
  inputTokens: number | null;
  outputTokens: number | null;
  steps: TraceStep[];
}
export interface Trace {
  traceId?: string;
  steps?: TraceStep[];
  gatewayCalls?: GatewayCall[];
  modelCalls?: number;
  tools?: string[];
  inputTokens?: number | null;
  outputTokens?: number | null;
  usageComplete?: boolean;
  orderId?: string;
  sequence?: number;
  citations?: Citation[];
}
export interface Message {
  role: string;
  content: string;
  sequence?: number;
  citations?: Citation[];
  trace?: Trace;
  orderId?: string;
}
export interface Doc {
  id: string;
  title: string;
  version: string;
  embeddingModel: string;
  createdAt: number;
  chunks: number;
}
export interface Run {
  id: string;
  mode: string;
  code: string;
  durationMs: number;
  createdAt: number;
  details: string;
}
export interface Settings {
  mode: string;
  model: string;
  embeddingModel: string;
  sessionMinutes: number;
  knowledgeStore: string;
  knowledgeScope: string;
}
let csrf = "";
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
  ) {
    super(message);
  }
}
export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (init.method && init.method !== "GET") headers.set("X-CSRF-TOKEN", csrf);
  const response = await fetch(`/api/platform${path}`, {
    ...init,
    headers,
    credentials: "same-origin",
    cache: "no-store",
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401)
      window.dispatchEvent(new Event("shopguard:unauthorized"));
    throw new ApiError(
      data.message || "操作暂时无法完成，请稍后重试",
      response.status,
      data.code || "REQUEST_FAILED",
    );
  }
  return data as T;
}
export async function currentSession() {
  const result = await request<{ user: Identity | null; csrf: string }>(
    "/session",
  );
  csrf = result.csrf;
  return result;
}
export const post = <T>(path: string, body: unknown = {}) =>
  request<T>(path, { method: "POST", body: JSON.stringify(body) });
export async function streamChat(
  sessionId: string,
  question: string,
  signal: AbortSignal,
  event: (type: string, data: unknown) => void,
  orderId?: string,
) {
  const response = await fetch("/api/platform/chat/stream", {
    method: "POST",
    credentials: "same-origin",
    signal,
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-TOKEN": csrf,
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ sessionId, question, orderId }),
  });
  if (!response.ok || !response.body) {
    const data = await response.json().catch(() => ({}));
    throw new ApiError(
      data.message || "无法开始回答，请刷新会话后重试",
      response.status,
      data.code || "STREAM_FAILED",
    );
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let buffer = "";
  let complete = false;
  try {
    while (true) {
      const part = await reader.read();
      buffer += decoder
        .decode(part.value, { stream: !part.done })
        .replace(/\r\n/g, "\n");
      if (buffer.length > 1_048_576) throw new Error("收到的响应过长");
      let boundary: number;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        const frame = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const type =
          frame
            .split("\n")
            .find((line) => line.startsWith("event:"))
            ?.slice(6)
            .trim() || "message";
        const data = frame
          .split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trimStart())
          .join("\n");
        if (!data) continue;
        const payload = JSON.parse(data);
        if (type === "error")
          throw new ApiError(
            payload.message || "回答未完成",
            503,
            payload.code,
          );
        if (type === "done") complete = true;
        event(type, payload);
      }
      if (part.done) break;
    }
    if (!complete) throw new Error("连接已结束，但没有收到完整回答，请重试");
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
