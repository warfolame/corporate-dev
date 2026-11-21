// Move Order Tracker: the auth page. One script, no libraries.
//
// The access token lives in exactly one place: the `accessToken` variable in this closure.
// It is never put into the URL, localStorage, sessionStorage, a cookie or the page text.
// Log out = set it to null. Server data is written with textContent only.
(() => {
  'use strict';

  /** The dev demo order from db/dev-data.sql: IN_PROGRESS again after every start of the dev profile. */
  const DEMO_ORDER_ID = '5eed0001-2026-4000-8000-000000000001';
  const LOG_LIMIT = 12;

  let accessToken = null;   // the JWT; only ever sent as "Authorization: Bearer ..."
  let expiresAt = 0;        // when the token stops working, in ms since epoch (from expiresIn)
  let countdownTimer = null;
  let signedInAs = null;    // { username, roles } from GET /api/me

  const $ = (id) => document.getElementById(id);

  // ---------------------------------------------------------------- HTTP

  function newCorrelationId() {
    const bytes = new Uint8Array(6);
    crypto.getRandomValues(bytes);
    return 'page-' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  }

  /**
   * One call to our API. Sends X-Correlation-Id, and the token when there is one.
   * Returns { status, json, tokenSent, correlationId }; status 0 means the server did not answer.
   */
  async function call(method, path, body, { withToken = true } = {}) {
    const correlationId = newCorrelationId();
    const headers = { 'Accept': 'application/json', 'X-Correlation-Id': correlationId };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const tokenSent = withToken && accessToken !== null;
    if (tokenSent) {
      headers['Authorization'] = 'Bearer ' + accessToken;
    }

    let status = 0;
    let json = null;
    try {
      const response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        cache: 'no-store',
        credentials: 'omit',
        redirect: 'error',
      });
      status = response.status;
      if ((response.headers.get('Content-Type') || '').includes('application/json')) {
        try {
          json = await response.json();
        } catch (ignored) {
          json = null;
        }
      }
    } catch (networkError) {
      status = 0;
    }

    const shownId = json && typeof json.correlationId === 'string' ? json.correlationId : correlationId;
    logRequest(method, path, status, tokenSent, shownId);
    return { status, json, tokenSent, correlationId: shownId };
  }

  // ---------------------------------------------------------------- DOM helpers

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function httpBadge(status) {
    const label = status === 0 ? 'no answer' : 'HTTP ' + status;
    const kind = status === 401 || status === 403 ? String(status) : String(Math.floor(status / 100));
    return el('span', 'http http--' + kind, label);
  }

  function statusChip(token) {
    const known = ['REQUESTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED'].includes(token);
    return el('span', 'status' + (known ? ' status--' + token : ''), token);
  }

  function show(box, kind) {
    box.className = 'outcome' + (kind ? ' outcome--' + kind : '');
    box.replaceChildren();
    box.hidden = false;
    return box;
  }

  function note(box, text) {
    box.append(el('p', null, text));
  }

  /** 400, 401, 403, 404, 409...: only code, message and correlationId from our error record. */
  function renderError(box, result, headline) {
    show(box, result.status === 0 ? 'warn' : 'error');
    const head = el('p', 'outcome__head');
    head.append(httpBadge(result.status));
    const json = result.json;
    if (json && typeof json.code === 'string') {
      head.append(el('code', null, json.code));
    }
    if (headline) {
      head.append(el('span', null, headline));
    }
    box.append(head);

    if (result.status === 0) {
      note(box, 'The server did not answer. Is "mvn spring-boot:run" running?');
    } else if (json && typeof json.message === 'string') {
      box.append(el('p', null, json.message));
      box.append(el('p', 'meta', 'correlationId: ' + (json.correlationId || result.correlationId)));
    } else {
      note(box, 'The response had no JSON error body.');
      box.append(el('p', 'meta', 'correlationId sent: ' + result.correlationId));
    }
    return box;
  }

  /** A move order from the API: the HTTP status and the status token from the JSON. */
  function renderOrder(box, result, headline) {
    show(box, 'ok');
    const order = result.json || {};
    const head = el('p', 'outcome__head');
    head.append(httpBadge(result.status), statusChip(order.status), el('span', null, headline));
    box.append(head);

    const fields = el('dl', 'fields');
    for (const [name, value] of [['businessKey', order.businessKey], ['title', order.title], ['id', order.id]]) {
      fields.append(el('dt', null, name), el('dd', 'mono', value));
    }
    box.append(fields);
    return box;
  }

  function info(box, text) {
    show(box, null);
    box.append(el('p', null, text));
  }

  /** While a call runs, the old answer is replaced, so a result on screen is always the latest one. */
  function pending(box, method, path) {
    info(box, method + ' ' + path + ' …');
  }

  // ---------------------------------------------------------------- request log

  function logRequest(method, path, status, tokenSent, correlationId) {
    const list = $('request-log');
    const item = el('li');
    item.append(httpBadge(status), el('strong', null, method), el('span', 'path', path));
    const time = new Date().toLocaleTimeString([], { hour12: false });
    item.append(el('span', 'meta',
        time + ' · token ' + (tokenSent ? 'sent (Bearer, hidden)' : 'not sent') + ' · correlationId ' + correlationId));
    list.prepend(item);
    while (list.children.length > LOG_LIMIT) {
      list.lastElementChild.remove();
    }
  }

  // ---------------------------------------------------------------- session

  function roleNames(authorities) {
    return (Array.isArray(authorities) ? authorities : [])
        .filter((a) => typeof a === 'string' && a.startsWith('ROLE_'))
        .map((a) => a.substring('ROLE_'.length));
  }

  function showSignedIn(me, expiresInSeconds) {
    signedInAs = { username: me.username, roles: roleNames(me.authorities) };
    const roles = signedInAs.roles.join(', ') || '(none)';
    $('me-username').textContent = signedInAs.username;
    $('me-role').textContent = roles;
    $('me-expires').textContent = Math.round(expiresInSeconds / 60) + ' min (expiresIn ' + expiresInSeconds + ' s)';
    $('pill-text').textContent = signedInAs.username + ' · ' + roles;

    $('login-card').hidden = true;
    $('session-card').hidden = false;
    $('session-pill').hidden = false;
    startCountdown();
  }

  function showSignedOut(message) {
    accessToken = null;
    expiresAt = 0;
    signedInAs = null;
    stopCountdown();

    $('session-card').hidden = true;
    $('session-pill').hidden = true;
    $('login-card').hidden = false;
    $('password').value = '';
    if (message) {
      info($('login-outcome'), message);
    }
    $('username').focus();
  }

  function startCountdown() {
    stopCountdown();
    const tick = () => {
      const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
      const mm = String(Math.floor(left / 60)).padStart(2, '0');
      const ss = String(left % 60).padStart(2, '0');
      $('me-countdown').textContent = left > 0 ? mm + ':' + ss : 'expired: the next call is 401';
    };
    tick();
    countdownTimer = setInterval(tick, 1000);
  }

  function stopCountdown() {
    if (countdownTimer !== null) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
  }

  /** A 401 although we sent a token: it expired or is no longer valid. Drop it, like a log out. */
  function dropRejectedToken(result) {
    if (result.status === 401 && result.tokenSent) {
      showSignedOut('The server no longer accepts this token (expired?). It was dropped: sign in again.');
    }
  }

  async function login(event) {
    event.preventDefault();
    const button = $('login-button');
    button.disabled = true;
    try {
      pending($('login-outcome'), 'POST', '/api/v1/auth/login');
      const body = { username: $('username').value, password: $('password').value };
      const result = await call('POST', '/api/v1/auth/login', body, { withToken: false });

      const json = result.json;
      if (result.status === 200 && json && typeof json.accessToken === 'string') {
        $('password').value = '';
        accessToken = json.accessToken;
        expiresAt = Date.now() + Number(json.expiresIn) * 1000;
        $('login-outcome').hidden = true;
        await loadMe(Number(json.expiresIn));
      } else {
        accessToken = null;
        renderError($('login-outcome'), result, 'not signed in');
      }
    } finally {
      button.disabled = false;
    }
  }

  /** Who am I, from the token the server just checked. Not from anything this page typed. */
  async function loadMe(expiresInSeconds) {
    const result = await call('GET', '/api/me');
    if (result.status === 200 && result.json) {
      showSignedIn(result.json, expiresInSeconds);
    } else {
      showSignedOut();
      renderError($('login-outcome'), result, 'GET /api/me failed');
    }
  }

  function logout() {
    showSignedOut('Signed out. The token was dropped: the next call from this page goes without it and is 401.');
  }

  // ---------------------------------------------------------------- move orders

  function orderId() {
    return $('order-id').value.trim();
  }

  function orderPath(id, suffix) {
    return '/api/v1/move-orders/' + encodeURIComponent(id) + (suffix || '');
  }

  function updateTarget() {
    const id = orderId();
    $('complete-target').textContent = id ? 'order ' + id : 'no order selected';
  }

  function newBusinessKey() {
    const n = new Uint16Array(1);
    crypto.getRandomValues(n);
    return 'MO-2026-' + String(1000 + (n[0] % 9000));
  }

  async function readOrder(event) {
    event.preventDefault();
    const box = $('order-outcome');
    const id = orderId();
    if (!id) {
      info(box, 'Enter an order id first, or use the dev demo order.');
      return;
    }
    pending(box, 'GET', orderPath(id));
    const result = await call('GET', orderPath(id));
    if (result.status === 200) {
      renderOrder(box, result, 'read');
    } else {
      renderError(box, result, 'read failed');
      dropRejectedToken(result);
    }
  }

  async function createOrder(event) {
    event.preventDefault();
    const box = $('order-outcome');
    const body = { businessKey: $('business-key').value, title: $('title').value };
    pending(box, 'POST', '/api/v1/move-orders');
    const result = await call('POST', '/api/v1/move-orders', body);
    if (result.status === 201 && result.json) {
      renderOrder(box, result, 'registered');
      note(box, 'A new order is REQUESTED. Completing it is refused by the status table (409) even for a manager: '
          + 'only an IN_PROGRESS order may be completed. Use the dev demo order to see a completion.');
      $('order-id').value = result.json.id;
      $('business-key').value = newBusinessKey();
      updateTarget();
    } else {
      renderError(box, result, 'not registered');
      dropRejectedToken(result);
    }
  }

  /** The locked verb. A dispatcher gets 403; the page then reads the order again to show it did not change. */
  async function completeOrder() {
    const box = $('complete-outcome');
    const id = orderId();
    if (!id) {
      info(box, 'Enter an order id in "Move order" first.');
      return;
    }
    const button = $('complete-button');
    button.disabled = true;
    try {
      pending(box, 'POST', orderPath(id, '/complete'));
      const result = await call('POST', orderPath(id, '/complete'));
      if (result.status === 200) {
        const who = signedInAs ? signedInAs.username : 'you';
        renderOrder(box, result, 'completed by ' + who);
        return;
      }

      renderError(box, result, 'not completed');
      if (result.status === 403) {
        const after = await call('GET', orderPath(id));
        if (after.status === 200 && after.json) {
          const line = el('p');
          line.append('Read again with GET: the status is still ', statusChip(after.json.status),
              '. The row did not change.');
          box.append(line);
        }
      } else if (result.status === 409) {
        note(box, 'The order did not change: only an IN_PROGRESS order can be completed.');
      } else if (result.status === 401 && !result.tokenSent) {
        note(box, 'No token was sent. Sign in first.');
      }
      dropRejectedToken(result);
    } finally {
      button.disabled = false;
    }
  }

  // ---------------------------------------------------------------- wiring

  function fillDevUser(event) {
    const button = event.target.closest('[data-fill]');
    if (!button) return;
    if (!$('login-card').hidden) {
      $('username').value = button.dataset.fill;
      $('password').value = button.dataset.password;
      $('login-button').focus();
    } else {
      info($('complete-outcome'), 'Log out first, then sign in as ' + button.dataset.fill + '.');
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('order-id').value = DEMO_ORDER_ID;
    $('business-key').value = newBusinessKey();
    updateTarget();

    $('login-form').addEventListener('submit', login);
    $('logout-button').addEventListener('click', logout);
    $('read-form').addEventListener('submit', readOrder);
    $('create-form').addEventListener('submit', createOrder);
    $('complete-button').addEventListener('click', completeOrder);
    $('order-id').addEventListener('input', updateTarget);
    $('use-demo').addEventListener('click', () => {
      $('order-id').value = DEMO_ORDER_ID;
      updateTarget();
    });
    document.querySelector('.users').addEventListener('click', fillDevUser);
  });
})();
