export class StompClient {
  constructor({ token, onMessage, onError, onStatus, onClose }) {
    this.token = token;
    this.onMessage = onMessage;
    this.onError = onError;
    this.onStatus = onStatus;
    this.onClose = onClose;
    this.socket = null;
    this.subscriptionSeq = 0;
    this.rejectConnect = null;
  }

  connect() {
    return new Promise((resolve, reject) => {
      if (!this.token) {
        reject(new Error("로그인이 필요해요."));
        return;
      }
      let connected = false;
      let settled = false;
      const rejectOnce = (error) => {
        if (settled) return;
        settled = true;
        this.rejectConnect = null;
        reject(error);
      };
      const resolveOnce = () => {
        if (settled) return;
        settled = true;
        this.rejectConnect = null;
        resolve();
      };
      this.rejectConnect = rejectOnce;
      this.socket = new WebSocket(resolveWsUrl());
      this.socket.addEventListener("open", () => {
        this.sendFrame("CONNECT", {
          "accept-version": "1.2",
          host: window.location.host,
          Authorization: `Bearer ${this.token}`,
        });
      });
      this.socket.addEventListener("message", (event) => {
        const data = String(event.data);
        this.handleFrames(data);
        if (data.startsWith("CONNECTED")) {
          connected = true;
          this.onStatus?.("채팅 서버에 연결됐어요.");
          resolveOnce();
        }
      });
      this.socket.addEventListener("close", () => {
        if (!connected) {
          rejectOnce(new Error("채팅 연결에 실패했어요."));
        }
        this.onClose?.(this);
        this.onStatus?.("채팅 연결이 닫혔어요.");
      });
      this.socket.addEventListener("error", () => {
        const error = new Error("채팅 연결에 실패했어요.");
        this.onError?.(error);
        rejectOnce(error);
      });
    });
  }

  disconnect() {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    this.sendFrame("DISCONNECT", {});
    this.socket.close();
  }

  subscribe(destination) {
    if (!this.isOpen()) {
      return null;
    }
    const id = `sub-${++this.subscriptionSeq}`;
    const headers = { id, destination, ack: "auto" };
    this.sendFrame("SUBSCRIBE", headers);
    return id;
  }

  unsubscribe(id) {
    if (!id || !this.isOpen()) return;
    this.sendFrame("UNSUBSCRIBE", { id });
  }

  send(destination, body) {
    if (!this.isOpen()) {
      throw new Error("채팅 서버에 먼저 연결해 주세요.");
    }
    this.sendFrame("SEND", { destination, "content-type": "application/json" }, JSON.stringify(body));
  }

  isOpen() {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  sendFrame(command, headers, body = "") {
    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
    this.socket.send(`${command}\n${headerLines.join("\n")}\n\n${body}\0`);
  }

  handleFrames(data) {
    data
      .split("\0")
      .map((frame) => frame.trim())
      .filter(Boolean)
      .forEach((frame) => this.handleFrame(frame));
  }

  handleFrame(frame) {
    const [head, body = ""] = frame.split("\n\n");
    const [command, ...headerLines] = head.split("\n");
    const headers = Object.fromEntries(
      headerLines
        .map((line) => line.split(":"))
        .filter(([key, value]) => key && value)
        .map(([key, ...rest]) => [key, rest.join(":")])
    );
    if (command === "MESSAGE") {
      this.onMessage?.(safeJson(body), headers);
      return;
    }
    if (command === "ERROR") {
      const error = new Error(safeJson(body)?.message || body || "채팅 오류가 발생했어요.");
      this.onError?.(error);
      this.rejectConnect?.(error);
      if (this.socket?.readyState === WebSocket.OPEN) {
        this.socket.close();
      }
    }
  }
}

function resolveWsUrl() {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws`;
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { content: text };
  }
}
