export class StompClient {
  constructor({ token, onMessage, onError, onStatus }) {
    this.token = token;
    this.onMessage = onMessage;
    this.onError = onError;
    this.onStatus = onStatus;
    this.socket = null;
    this.subscriptionSeq = 0;
  }

  connect() {
    return new Promise((resolve, reject) => {
      if (!this.token) {
        reject(new Error("로그인이 필요해요."));
        return;
      }
      this.socket = new WebSocket(resolveWsUrl());
      this.socket.addEventListener("open", () => {
        this.sendFrame("CONNECT", {
          "accept-version": "1.2",
          host: window.location.host,
          Authorization: `Bearer ${this.token}`,
        });
      });
      this.socket.addEventListener("message", (event) => {
        this.handleFrames(String(event.data));
        if (String(event.data).startsWith("CONNECTED")) {
          this.onStatus?.("채팅 서버에 연결됐어요.");
          resolve();
        }
      });
      this.socket.addEventListener("close", () => this.onStatus?.("채팅 연결이 닫혔어요."));
      this.socket.addEventListener("error", () => {
        const error = new Error("채팅 연결에 실패했어요.");
        this.onError?.(error);
        reject(error);
      });
    });
  }

  disconnect() {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    this.sendFrame("DISCONNECT", {});
    this.socket.close();
  }

  subscribe(destination) {
    if (!this.isOpen()) return null;
    const id = `sub-${++this.subscriptionSeq}`;
    this.sendFrame("SUBSCRIBE", { id, destination });
    return id;
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
      this.onError?.(new Error(safeJson(body)?.message || body || "채팅 오류가 발생했어요."));
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
