import ws from 'k6/ws';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  baseUrl,
  booleanEnv,
  createSummary,
  nonNegativeIntegerEnv,
  numberCsvEnv,
  positiveIntegerEnv,
  wsUrlFromBaseUrl,
} from './lib/common.js';
import {
  credentialPoolFromEnv,
  loginUser,
  loginUsers,
  requireCredentialCount,
  signupUsers,
} from './lib/auth.js';
import { createChatRoom, createProduct, fetchCategoryIds } from './lib/data.js';

const BASE_URL = baseUrl();
const WS_URL = `${wsUrlFromBaseUrl(BASE_URL)}/ws`;
const VUS = positiveIntegerEnv('VUS', 10);
const DURATION = __ENV.DURATION || '1m';
const CONNECTION_DURATION_MS = positiveIntegerEnv('CONNECTION_DURATION_MS', 60000);
const CONNECT_TIMEOUT_MS = positiveIntegerEnv('CONNECT_TIMEOUT_MS', 5000);
const MESSAGE_INTERVAL_MS = positiveIntegerEnv('MESSAGE_INTERVAL_MS', 1000);
const SUBSCRIBE_READY_TIMEOUT_MS = positiveIntegerEnv('SUBSCRIBE_READY_TIMEOUT_MS', 1000);
const CATEGORY_ID = nonNegativeIntegerEnv('CATEGORY_ID', 0);
const AUTO_SIGNUP_CHAT_USERS = booleanEnv('AUTO_SIGNUP_CHAT_USERS', true);
const DEFAULT_PASSWORD = __ENV.PERF_RUN_KEY ? 'password' : 'Password123!';
const SUMMARY_BASENAME = __ENV.SUMMARY_BASENAME || 'chat-websocket-summary';
const SUMMARY_DIRECTORY = __ENV.SUMMARY_DIRECTORY || 'k6/results';

const chatWsConnected = new Counter('chat_ws_connected');
const chatSubscriptionsReady = new Counter('chat_subscriptions_ready');
const chatMessagesSent = new Counter('chat_messages_sent');
const chatMessagesAccepted = new Counter('chat_messages_accepted');
const chatMessageFrames = new Counter('chat_message_frames');
const chatErrors = new Counter('chat_errors');
const chatMessageLatency = new Trend('chat_message_latency');

export const options = {
  scenarios: {
    chat_websocket: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '10s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    chat_ws_connected: ['count>0'],
    chat_messages_accepted: ['count>0'],
    chat_errors: ['count==0'],
    chat_message_latency: ['p(95)<1000', 'p(99)<2000'],
    ws_connecting: ['p(95)<1000', 'p(99)<2000'],
  },
};

export function setup() {
  const credentials = chatUserCredentials();
  requireCredentialCount(credentials, VUS, 'chat-websocket users');
  const users = loginUsers(BASE_URL, credentials.slice(0, VUS), 'setupChatUserLogin');
  const roomIds = chatRoomIds(users);

  return {
    sessions: users.map((user, index) => ({
      accessToken: user.accessToken,
      roomId: roomIds[index % roomIds.length],
    })),
  };
}

export default function (data) {
  const session = data.sessions[(exec.vu.idInTest - 1) % data.sessions.length];
  const pendingSentAtByContent = {};
  let connected = false;
  let senderStarted = false;
  let messageSequence = 0;
  const messagePrefix = `k6-vu-${exec.vu.idInTest}-iter-${exec.scenario.iterationInTest}`;

  const response = ws.connect(WS_URL, { tags: { endpoint: 'chatWebSocket' } }, (socket) => {
    socket.on('open', () => {
      socket.send(stompFrame('CONNECT', {
        'accept-version': '1.2',
        host: 'localhost',
        Authorization: `Bearer ${session.accessToken}`,
      }));
    });

    socket.on('message', (rawMessage) => {
      parseFrames(rawMessage).forEach((frame) => {
        if (frame.command === 'CONNECTED') {
          connected = true;
          chatWsConnected.add(1);
          subscribe(socket, session.roomId);
          socket.setTimeout(() => {
            startSenderIfNeeded();
          }, SUBSCRIBE_READY_TIMEOUT_MS);
          return;
        }

        if (frame.command === 'RECEIPT') {
          if (isMainSubscriptionReceipt(frame)) {
            chatSubscriptionsReady.add(1);
            startSenderIfNeeded();
          }
          return;
        }

        if (frame.command === 'MESSAGE') {
          handleMessageFrame(frame, pendingSentAtByContent);
          return;
        }

        if (frame.command === 'ERROR') {
          chatErrors.add(1);
          socket.close();
        }
      });
    });

    socket.on('error', () => {
      chatErrors.add(1);
    });

    socket.setTimeout(() => {
      if (!connected) {
        chatErrors.add(1);
        socket.close();
      }
    }, CONNECT_TIMEOUT_MS);

    socket.setTimeout(() => {
      socket.send(stompFrame('DISCONNECT', { receipt: `close-${Date.now()}` }));
      socket.close();
    }, CONNECTION_DURATION_MS);

    function startSenderIfNeeded() {
      if (senderStarted) {
        return;
      }
      senderStarted = true;
      socket.setInterval(() => {
        messageSequence += 1;
        sendChatMessage(socket, session.roomId, pendingSentAtByContent, `${messagePrefix}-${messageSequence}`);
      }, MESSAGE_INTERVAL_MS);
    }
  });

  check(response, {
    'websocket upgrade status is 101': (res) => res && res.status === 101,
  });
}

export function handleSummary(data) {
  return createSummary(data, {
    title: 'k6 Chat WebSocket Summary',
    consoleTitle: 'k6 chat websocket',
    summaryBasename: SUMMARY_BASENAME,
    summaryDirectory: SUMMARY_DIRECTORY,
    runLines: [
      `- Base URL: ${BASE_URL}`,
      `- WebSocket URL: ${WS_URL}`,
      `- Target VUs: ${VUS}`,
      `- Duration: ${DURATION}`,
      `- Connection duration ms: ${CONNECTION_DURATION_MS}`,
      `- Message interval ms: ${MESSAGE_INTERVAL_MS}`,
      `- Subscribe ready timeout ms: ${SUBSCRIBE_READY_TIMEOUT_MS}`,
      `- Auto signup chat users: ${AUTO_SIGNUP_CHAT_USERS}`,
    ],
  });
}

function chatUserCredentials() {
  const credentials = credentialPoolFromEnv({
    emailsEnv: 'CHAT_USER_EMAILS',
    emailEnv: 'CHAT_USER_EMAIL',
    passwordsEnv: 'CHAT_USER_PASSWORDS',
    passwordEnv: 'CHAT_USER_PASSWORD',
    sequenceStartEnv: 'CHAT_USER_SEQUENCE_START',
    defaultSequenceStart: 2,
    count: VUS,
    defaultPassword: DEFAULT_PASSWORD,
    defaultEmail: __ENV.AUTH_EMAIL,
  });

  if (credentials.length > 0 || !AUTO_SIGNUP_CHAT_USERS) {
    return credentials;
  }

  const generatedCredentials = generatedChatCredentials(
      'k6-chat-user',
      VUS,
      __ENV.CHAT_USER_PASSWORD || DEFAULT_PASSWORD,
      'k6-chat-user');
  signupUsers(BASE_URL, generatedCredentials, 'setupChatUserSignup');
  return generatedCredentials;
}

function chatRoomIds(users) {
  const configuredRoomIds = numberCsvEnv('CHAT_ROOM_IDS');
  if (configuredRoomIds.length > 0) {
    return configuredRoomIds;
  }

  const sellerCredentials = credentialPoolFromEnv({
    emailsEnv: 'SELLER_EMAILS',
    emailEnv: 'SELLER_EMAIL',
    passwordsEnv: 'SELLER_PASSWORDS',
    passwordEnv: 'SELLER_PASSWORD',
    sequenceStartEnv: 'SELLER_SEQUENCE_START',
    defaultSequenceStart: 1,
    count: 1,
    defaultPassword: DEFAULT_PASSWORD,
  });
  if (sellerCredentials.length === 0 && AUTO_SIGNUP_CHAT_USERS) {
    const generatedSellerCredentials = generatedChatCredentials(
        'k6-chat-seller',
        1,
        __ENV.SELLER_PASSWORD || DEFAULT_PASSWORD,
        'k6-chat-seller');
    signupUsers(BASE_URL, generatedSellerCredentials, 'setupChatSellerSignup');
    sellerCredentials.push(generatedSellerCredentials[0]);
  }
  requireCredentialCount(sellerCredentials, 1, 'chat-websocket seller');
  const seller = loginUser(BASE_URL, sellerCredentials[0], 'setupSellerLogin');
  const categoryId = CATEGORY_ID > 0 ? CATEGORY_ID : firstCategoryId();
  const productId = createProduct(BASE_URL, seller.accessToken, categoryId, 'k6 chat websocket');

  return users.map((user) => createChatRoom(BASE_URL, user.accessToken, productId));
}

function firstCategoryId() {
  const categoryIds = fetchCategoryIds(BASE_URL);
  if (categoryIds.length === 0) {
    throw new Error('No category id is available. Set CATEGORY_ID or seed categories first.');
  }
  return categoryIds[0];
}

function generatedChatCredentials(emailPrefix, count, password, nicknamePrefix) {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
  return Array.from({ length: count }, (_, index) => ({
    email: `${emailPrefix}-${runId}-${index + 1}@example.com`,
    password: password,
    nickname: `${nicknamePrefix}-${index + 1}`,
  }));
}

function subscribe(socket, roomId) {
  const mainReceiptId = `sub-room-${roomId}-${Date.now()}`;
  socket.send(stompFrame('SUBSCRIBE', {
    id: `room-${roomId}-${Date.now()}`,
    destination: `/sub/chat-rooms/${roomId}`,
    ack: 'auto',
    receipt: mainReceiptId,
  }));
  socket.send(stompFrame('SUBSCRIBE', {
    id: `room-errors-${roomId}-${Date.now()}`,
    destination: `/sub/chat-rooms/${roomId}/errors`,
    ack: 'auto',
  }));
  socket.send(stompFrame('SUBSCRIBE', {
    id: `user-errors-${roomId}-${Date.now()}`,
    destination: '/user/queue/errors',
    ack: 'auto',
  }));
}

function sendChatMessage(socket, roomId, pendingSentAtByContent, content) {
  pendingSentAtByContent[content] = Date.now();
  chatMessagesSent.add(1);
  socket.send(stompFrame(
      'SEND',
      {
        destination: `/pub/chat-rooms/${roomId}/message`,
      },
      JSON.stringify({ content: content }),
  ));
}

function handleMessageFrame(frame, pendingSentAtByContent) {
  chatMessageFrames.add(1);
  if (frame.headers.destination && frame.headers.destination.includes('/errors')) {
    chatErrors.add(1);
    return;
  }

  try {
    const body = JSON.parse(frame.body || '{}');
    if (!body.content || pendingSentAtByContent[body.content] === undefined) {
      return;
    }
    chatMessagesAccepted.add(1);
    chatMessageLatency.add(Date.now() - pendingSentAtByContent[body.content]);
    delete pendingSentAtByContent[body.content];
  } catch (error) {
    chatErrors.add(1);
  }
}

function stompFrame(command, headers, body) {
  const frameBody = body || '';
  const headerLines = Object.keys(headers || {})
      .map((name) => `${name}:${headers[name]}`)
      .join('\n');
  return `${command}\n${headerLines}\n\n${frameBody}\x00`;
}

function isMainSubscriptionReceipt(frame) {
  const receiptId = frame.headers['receipt-id'] || '';
  return receiptId.startsWith('sub-room-');
}

function parseFrames(rawMessage) {
  return String(rawMessage)
      .split('\x00')
      .map((frame) => frame.trim())
      .filter((frame) => frame !== '')
      .map(parseFrame);
}

function parseFrame(rawFrame) {
  const separatorIndex = rawFrame.indexOf('\n\n');
  const headerBlock = separatorIndex >= 0 ? rawFrame.substring(0, separatorIndex) : rawFrame;
  const body = separatorIndex >= 0 ? rawFrame.substring(separatorIndex + 2) : '';
  const lines = headerBlock.split('\n');
  const command = lines.shift();
  const headers = {};

  lines.forEach((line) => {
    const index = line.indexOf(':');
    if (index > 0) {
      headers[line.substring(0, index)] = line.substring(index + 1);
    }
  });

  return {
    command: command,
    headers: headers,
    body: body,
  };
}
