import http from 'k6/http';
import { check } from 'k6';
import { csvEnv, isSuccessfulApiResponse, positiveIntegerEnv, requireApiData } from './common.js';

export function credentialPoolFromEnv(options) {
  const count = options.count || 1;
  const emails = csvEnv(options.emailsEnv || 'AUTH_EMAILS');
  const passwords = csvEnv(options.passwordsEnv || 'AUTH_PASSWORDS');
  const password = __ENV[options.passwordEnv || 'AUTH_PASSWORD'] || options.defaultPassword || 'password';

  if (emails.length > 0) {
    return emails.map((email, index) => ({
      email: email,
      password: passwords[index] || password,
    }));
  }

  const runKey = __ENV[options.runKeyEnv || 'PERF_RUN_KEY'];
  if (runKey) {
    const sequenceStart = positiveIntegerEnv(
        options.sequenceStartEnv || 'PERF_USER_SEQUENCE_START',
        options.defaultSequenceStart || 1);
    return Array.from({ length: count }, (_, index) => ({
      email: perfUserEmail(runKey, sequenceStart + index),
      password: password,
    }));
  }

  const email = __ENV[options.emailEnv || 'AUTH_EMAIL'] || options.defaultEmail;
  if (!email) {
    return [];
  }
  return [{ email: email, password: password }];
}

export function requireCredentialCount(credentials, count, context) {
  if (credentials.length < count) {
    throw new Error(`${context} requires at least ${count} credentials, but got ${credentials.length}.`);
  }
}

export function signupUser(baseUrl, credential, endpointTag) {
  const response = http.post(
      `${baseUrl}/api/auth/signup`,
      JSON.stringify({
        email: credential.email,
        password: credential.password,
        nickname: credential.nickname,
      }),
      {
        headers: jsonHeaders(),
        tags: { endpoint: endpointTag || 'authSignup' },
      },
  );

  if (response.status !== 201 || !isSuccessfulApiResponse(response)) {
    throw new Error(
        `signup failed for ${credential.email}. status=${response.status}, body=${bodyPreview(response)}`);
  }

  return requireApiData(response, `signup ${credential.email}`);
}

export function signupUsers(baseUrl, credentials, endpointTag) {
  return credentials.map((credential) => signupUser(baseUrl, credential, endpointTag));
}

export function loginUser(baseUrl, credential, endpointTag) {
  const response = http.post(
      `${baseUrl}/api/auth/login`,
      JSON.stringify({ email: credential.email, password: credential.password }),
      {
        headers: jsonHeaders(),
        tags: { endpoint: endpointTag || 'authLogin' },
      },
  );

  check(response, {
    [`${endpointTag || 'authLogin'} status is 200`]: (res) => res.status === 200,
    [`${endpointTag || 'authLogin'} response is successful`]: isSuccessfulApiResponse,
  });

  const data = requireApiData(response, `login ${credential.email}`);
  return {
    email: credential.email,
    userId: data.user && data.user.userId,
    accessToken: data.accessToken,
    refreshCookie: refreshCookie(response),
  };
}

export function loginUsers(baseUrl, credentials, endpointTag) {
  return credentials.map((credential) => loginUser(baseUrl, credential, endpointTag));
}

export function authHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };
}

export function jsonHeaders() {
  return {
    'Content-Type': 'application/json',
  };
}

export function refreshCookie(response) {
  const cookieName = __ENV.REFRESH_COOKIE_NAME || 'refreshToken';
  const cookies = response.cookies && response.cookies[cookieName];
  if (cookies && cookies.length > 0 && cookies[0].value) {
    return `${cookieName}=${cookies[0].value}`;
  }

  const header = response.headers['Set-Cookie'] || response.headers['set-cookie'];
  if (!header) {
    return '';
  }
  const match = header.match(new RegExp(`${cookieName}=([^;]+)`));
  if (!match) {
    return '';
  }
  return `${cookieName}=${match[1]}`;
}

function perfUserEmail(runKey, sequence) {
  return `perf-user-${runKey}-${String(sequence).padStart(5, '0')}@example.com`;
}

function bodyPreview(response) {
  const body = response.body || '';
  if (body.length <= 500) {
    return body;
  }
  return `${body.substring(0, 500)}...`;
}
