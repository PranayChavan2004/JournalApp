import { api, setUnauthorizedHandler } from './api.js';
import { h, toast, setError, moodLabel, formatTime, dayKey, dayLabel, shortDay } from './ui.js';

const $ = (selector, root = document) => root.querySelector(selector);

const state = {
  user: null,
  entries: [],
  filterMood: 'ALL',
  query: '',
  googleEnabled: false,
};

const AUTH_MESSAGES = {
  account_exists: 'An account with that e-mail already exists. Sign in with your password instead.',
  unverified: 'Google could not confirm that e-mail address, so we could not sign you in.',
  failed: 'Google sign-in did not complete. Please try again.',
};

const CITY_KEY = 'journal.city';

 // Start-up
async function init() {
  setUnauthorizedHandler(() => {
    if (state.user) {
      state.user = null;
      showAuth('Your session ended. Please sign in again.');
    }
  });

  bindStatic();

  try {
    const config = await api('/public/config');
    state.googleEnabled = Boolean(config && config.googleEnabled);
  } catch {
    /* the Google button just stays hidden */
  }
  $('#oauth-block').hidden = !state.googleEnabled;

  // Google sends people back to /?auth_error=... when something went wrong
  const params = new URLSearchParams(location.search);
  const authError = params.get('auth_error');
  if (authError) history.replaceState(null, '', location.pathname + location.hash);

  try {
    state.user = await api('/user/me');
  } catch {
    state.user = null;
  }

  if (state.user) showApp();
  else showAuth(authError ? AUTH_MESSAGES[authError] || AUTH_MESSAGES.failed : '');
}

// Signed-out screen
function showAuth(message) {
  $('#app-view').hidden = true;
  $('#auth-view').hidden = false;
  const note = $('#auth-message');
  note.textContent = message || '';
  note.hidden = !message;
  buildAuthRibbon();
  $('#login-form').elements.password.value = '';
}

function selectTab(which) {
  const login = which === 'login';
  $('#tab-login').setAttribute('aria-selected', String(login));
  $('#tab-signup').setAttribute('aria-selected', String(!login));
  $('#tab-login').tabIndex = login ? 0 : -1;
  $('#tab-signup').tabIndex = login ? -1 : 0;
  $('#login-form').hidden = !login;
  $('#signup-form').hidden = login;
  $('#auth-message').hidden = true;
}

async function login(username, password) {
  const result = await api('/public/login', { method: 'POST', body: { username, password } });
  state.user = result.user;
  showApp();
}

async function onLogin(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('button[type="submit"]', form);
  setError(form, '');
  button.disabled = true;
  try {
    await login(form.elements.username.value.trim(), form.elements.password.value);
    form.reset();
  } catch (err) {
    setError(form, err.message);
  } finally {
    button.disabled = false;
  }
}

async function onSignup(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('button[type="submit"]', form);
  setError(form, '');
  button.disabled = true;
  const body = {
    userName: form.elements.userName.value.trim(),
    email: form.elements.email.value.trim() || null,
    password: form.elements.password.value,
    sentimentAnalysis: form.elements.sentimentAnalysis.checked,
  };
  try {
    await api('/public/signup', { method: 'POST', body });
    await login(body.userName, body.password); // sign them straight in
    form.reset();
  } catch (err) {
    setError(form, err.message);
  } finally {
    button.disabled = false;
  }
}

async function logout() {
  try {
    await api('/public/logout', { method: 'POST' });
  } catch {
    /* even if this fails, drop the local state */
  }
  state.user = null;
  state.entries = [];
  location.hash = '#/';
  showAuth('');
}

// decorative ribbon on the sign-in screen: a fixed, made-up month
function buildAuthRibbon() {
  const box = $('#auth-ribbon');
  if (box.childElementCount) return;
  const pattern = [
    ['HAPPY', 2], [null, 0], ['HAPPY', 1], ['SAD', 1], [null, 0], ['ANXIOUS', 2], ['ANXIOUS', 1],
    ['SAD', 3], [null, 0], [null, 0], ['HAPPY', 1], ['HAPPY', 3], ['HAPPY', 2], ['ANGRY', 1],
    ['ANGRY', 2], ['SAD', 1], [null, 0], ['ANXIOUS', 1], ['HAPPY', 1], [null, 0], ['HAPPY', 2],
    ['HAPPY', 3], ['SAD', 2], [null, 0], ['HAPPY', 1], ['ANXIOUS', 1], ['HAPPY', 2], [null, 0],
    ['HAPPY', 1], ['HAPPY', 3],
  ];
  pattern.forEach(([mood, size], i) => box.append(makeTick(mood, size, i)));
  box.classList.add('play');
}

function makeTick(mood, size, index, title) {
  const tick = h('span', { class: `tick t${size} ${mood ? 'm-' + mood : 'm-NONE'}`, title });
  tick.style.setProperty('--i', String(index));
  return tick;
}

// Signed-in shell + routing
const ROUTES = {
  '/': renderJournal,
  '/account': renderAccount,
  '/admin': renderAdmin,
};

function isAdmin() {
  return Boolean(state.user && state.user.roles && state.user.roles.includes('ADMIN'));
}

function displayName(user) {
  return user.username.includes('@') ? user.username.split('@')[0] : user.username;
}

function showApp() {
  $('#auth-view').hidden = true;
  $('#app-view').hidden = false;
  $('#nav-admin').hidden = !isAdmin();
  route();
}

function route() {
  if (!state.user) return;
  let path = location.hash.replace(/^#/, '') || '/';
  if (!ROUTES[path] || (path === '/admin' && !isAdmin())) path = '/';

  $('#view-journal').hidden = path !== '/';
  $('#view-account').hidden = path !== '/account';
  $('#view-admin').hidden = path !== '/admin';
  document.querySelectorAll('.nav a').forEach((a) => {
    if (a.dataset.route === path) a.setAttribute('aria-current', 'page');
    else a.removeAttribute('aria-current');
  });
  document.title = path === '/' ? 'Journal' : path === '/account' ? 'Account · Journal' : 'Users · Journal';
  window.scrollTo(0, 0);
  ROUTES[path]();
}

// Journal view
async function renderJournal() {
  const hour = new Date().getHours();
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  $('#greeting').textContent = `${part}, ${displayName(state.user)}`;
  loadWeather();

  try {
    state.entries = await api('/journal');
  } catch (err) {
    if (err.status !== 401) toast(err.message, 'error');
  }
  renderRibbon(true);
  renderEntries();
}

async function loadWeather() {
  const line = $('#weather-line');
  line.hidden = true;
  let city = '';
  try {
    city = localStorage.getItem(CITY_KEY) || '';
  } catch {
    /* storage may be blocked */
  }
  try {
    const w = await api('/user/weather' + (city ? `?city=${encodeURIComponent(city)}` : ''));
    if (w && w.available) {
      const desc = w.description ? ` ${w.description}.` : '';
      line.textContent = `${w.temperature}°C in ${w.city}, feels like ${w.feelsLike}°C.${desc}`;
      line.hidden = false;
    }
  } catch {
    /* weather is a nice-to-have */
  }
}

function renderRibbon(animate) {
  const ribbon = $('#ribbon');
  ribbon.replaceChildren();
  ribbon.classList.remove('play');

  const days = new Map();
  for (const entry of state.entries) {
    if (!entry.date) continue;
    const key = dayKey(new Date(entry.date));
    const day = days.get(key) || { count: 0, moods: {} };
    day.count += 1;
    if (entry.sentiment) day.moods[entry.sentiment] = (day.moods[entry.sentiment] || 0) + 1;
    days.set(key, day);
  }

  let active = 0;
  let totals = {};
  for (let i = 29; i >= 0; i--) {
    const date = new Date();
    date.setDate(date.getDate() - i);
    const day = days.get(dayKey(date));
    let mood = null;
    let title = `${shortDay(date)}: no entry`;
    let size = 0;
    if (day) {
      active += 1;
      size = Math.min(day.count, 3);
      const ranked = Object.entries(day.moods).sort((a, b) => b[1] - a[1]);
      mood = ranked.length ? ranked[0][0] : null;
      if (mood) totals[mood] = (totals[mood] || 0) + 1;
      title = `${shortDay(date)}: ${day.count} ${day.count === 1 ? 'entry' : 'entries'}${mood ? ', mostly ' + moodLabel(mood).toLowerCase() : ''}`;
    }
    const tick = makeTick(mood, size, 29 - i, title);
    if (i === 0) tick.classList.add('today');
    ribbon.append(tick);
  }

  const top = Object.entries(totals).sort((a, b) => b[1] - a[1])[0];
  ribbon.setAttribute(
    'aria-label',
    active === 0
      ? 'No entries in the last 30 days.'
      : `You wrote on ${active} of the last 30 days${top ? `, most often feeling ${moodLabel(top[0]).toLowerCase()}` : ''}.`
  );
  if (animate) {
    void ribbon.offsetWidth; // restart the animation
    ribbon.classList.add('play');
  }
}

function renderEntries() {
  const box = $('#entries');
  box.replaceChildren();

  const q = state.query.trim().toLowerCase();
  const list = state.entries.filter((e) => {
    if (state.filterMood !== 'ALL' && e.sentiment !== state.filterMood) return false;
    if (!q) return true;
    return `${e.title} ${e.content || ''}`.toLowerCase().includes(q);
  });

  if (!list.length) {
    const filtering = state.entries.length > 0;
    box.append(
      h('div', { class: 'empty' },
        h('strong', null, filtering ? 'No entries match' : 'Your journal is empty'),
        filtering ? 'Try a different search or mood.' : 'Write your first entry above. It only takes a minute.')
    );
    return;
  }

  let lastKey = '';
  for (const entry of list) {
    const when = new Date(entry.date);
    const key = dayKey(when);
    if (key !== lastKey) {
      box.append(h('h2', { class: 'day' }, dayLabel(when)));
      lastKey = key;
    }
    box.append(entryNode(entry, when));
  }
}

function entryNode(entry, when) {
  const content = entry.content || '';
  const long = content.length > 320 || content.split('\n').length > 5;
  const text = h('p', { class: 'entry-text' + (long ? ' clamp' : '') }, content);

  const meta = h('div', { class: 'entry-meta' },
    h('span', { class: 'mood-tag' }, h('i', { class: 'dot' }), entry.sentiment ? moodLabel(entry.sentiment) : 'No mood')
  );
  if (long) {
    const more = h('button', { type: 'button', class: 'link-btn', 'aria-expanded': 'false' }, 'Read more');
    more.addEventListener('click', () => {
      const open = text.classList.toggle('clamp') === false;
      more.textContent = open ? 'Show less' : 'Read more';
      more.setAttribute('aria-expanded', String(open));
    });
    meta.append(more);
  }
  meta.append(
    h('button', { type: 'button', class: 'link-btn', onClick: () => openEdit(entry) }, 'Edit'),
    h('button', { type: 'button', class: 'link-btn danger', onClick: () => removeEntry(entry) }, 'Delete')
  );

  return h('article', { class: `entry ${entry.sentiment ? 'm-' + entry.sentiment : 'm-NONE'}` },
    h('div', { class: 'entry-when' }, h('time', { datetime: when.toISOString() }, formatTime(when))),
    h('div', { class: 'entry-body' }, h('h3', null, entry.title), text, meta)
  );
}

async function onCompose(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('button[type="submit"]', form);
  const title = form.elements.title.value.trim();
  setError(form, '');
  if (!title) {
    setError(form, 'Give your entry a title.');
    form.elements.title.focus();
    return;
  }
  button.disabled = true;
  try {
    const created = await api('/journal', {
      method: 'POST',
      body: { title, content: form.elements.content.value, sentiment: form.elements.mood.value || null },
    });
    state.entries.unshift(created);
    form.reset();
    paintComposer();
    renderRibbon(false);
    renderEntries();
    toast('Entry saved');
  } catch (err) {
    setError(form, err.message);
  } finally {
    button.disabled = false;
  }
}

// the composer takes on the colour of the mood that is selected
function paintComposer() {
  const form = $('#composer');
  const value = form.elements.mood.value;
  form.classList.remove('m-HAPPY', 'm-SAD', 'm-ANGRY', 'm-ANXIOUS');
  if (value) form.classList.add('m-' + value);
}

/* ----- edit / delete ----- */
let editing = null;

function openEdit(entry) {
  editing = entry;
  const form = $('#edit-form');
  setError(form, '');
  form.elements.title.value = entry.title;
  form.elements.content.value = entry.content || '';
  form.elements.mood.value = entry.sentiment || '';
  $('#edit-dlg').showModal();
  form.elements.title.focus();
}

async function onEditSave(event) {
  event.preventDefault();
  if (!editing) return;
  const form = event.currentTarget;
  const title = form.elements.title.value.trim();
  if (!title) {
    setError(form, 'Give your entry a title.');
    return;
  }
  try {
    const updated = await api(`/journal/${encodeURIComponent(editing.id)}`, {
      method: 'PUT',
      body: { title, content: form.elements.content.value, sentiment: form.elements.mood.value || null },
    });
    state.entries = state.entries.map((e) => (e.id === updated.id ? updated : e));
    $('#edit-dlg').close();
    renderRibbon(false);
    renderEntries();
    toast('Changes saved');
  } catch (err) {
    setError(form, err.message);
  }
}

function confirmDialog({ title, message, confirmLabel, cancelLabel }) {
  const dlg = $('#confirm-dlg');
  $('#confirm-title').textContent = title;
  $('#confirm-text').textContent = message;
  $('#confirm-ok').textContent = confirmLabel;
  $('#confirm-cancel').textContent = cancelLabel;
  return new Promise((resolve) => {
    dlg.addEventListener('close', () => resolve(dlg.returnValue === 'ok'), { once: true });
    dlg.returnValue = 'cancel';
    dlg.showModal();
  });
}

async function removeEntry(entry) {
  const ok = await confirmDialog({
    title: 'Delete this entry?',
    message: `“${entry.title}” will be permanently deleted.`,
    confirmLabel: 'Delete entry',
    cancelLabel: 'Keep entry',
  });
  if (!ok) return;
  try {
    await api(`/journal/${encodeURIComponent(entry.id)}`, { method: 'DELETE' });
    state.entries = state.entries.filter((e) => e.id !== entry.id);
    renderRibbon(false);
    renderEntries();
    toast('Entry deleted');
  } catch (err) {
    toast(err.message, 'error');
  }
}

// Account view
function renderAccount() {
  const user = state.user;
  const google = user.provider === 'GOOGLE';
  $('#acc-username').textContent = user.username;
  $('#acc-provider').textContent = google ? 'Google' : 'Username and password';

  const prefs = $('#prefs-form');
  setError(prefs, '');
  prefs.elements.email.value = user.email || '';
  prefs.elements.email.readOnly = google;
  prefs.elements.sentimentAnalysis.checked = user.sentimentAnalysis;

  const cityForm = $('#city-form');
  setError(cityForm, '');
  try {
    cityForm.elements.city.value = localStorage.getItem(CITY_KEY) || '';
  } catch {

  }

  $('#password-form').hidden = google;
  setError($('#password-form'), '');
  $('#password-form').reset();

  const del = $('#delete-form');
  del.reset();
  setError(del, '');
  $('button', del).disabled = true;
}

async function onPrefs(event) {
  event.preventDefault();
  const form = event.currentTarget;
  setError(form, '');
  try {
    state.user = await api('/user', {
      method: 'PUT',
      body: { email: form.elements.email.value.trim(), sentimentAnalysis: form.elements.sentimentAnalysis.checked },
    });
    toast('Preferences saved');
  } catch (err) {
    setError(form, err.message);
  }
}

function onCity(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const city = form.elements.city.value.trim();
  try {
    if (city) localStorage.setItem(CITY_KEY, city);
    else localStorage.removeItem(CITY_KEY);
    toast(city ? 'City saved' : 'City reset');
  } catch {
    setError(form, 'This browser is blocking local storage, so the city cannot be saved.');
  }
}

async function onPassword(event) {
  event.preventDefault();
  const form = event.currentTarget;
  setError(form, '');
  try {
    await api('/user', {
      method: 'PUT',
      body: { currentPassword: form.elements.currentPassword.value, newPassword: form.elements.newPassword.value },
    });
    form.reset();
    toast('Password changed');
  } catch (err) {
    setError(form, err.message);
  }
}

async function onDelete(event) {
  event.preventDefault();
  const form = event.currentTarget;
  if (form.elements.confirm.value !== 'DELETE') return;
  try {
    await api('/user', { method: 'DELETE' });
    state.user = null;
    state.entries = [];
    location.hash = '#/';
    showAuth('Your account has been deleted.');
  } catch (err) {
    setError(form, err.message);
  }
}

// Admin view
async function renderAdmin() {
  const body = $('#admin-rows');
  body.replaceChildren();
  try {
    const users = await api('/admin/all-users');
    $('#admin-count').textContent = `${users.length} ${users.length === 1 ? 'user' : 'users'}`;
    for (const u of users) {
      body.append(
        h('tr', null,
          h('td', null, u.username),
          h('td', null, u.email || ''),
          h('td', null, (u.roles || []).join(', ')),
          h('td', null, u.provider === 'GOOGLE' ? 'Google' : 'Password'))
      );
    }
  } catch (err) {
    $('#admin-count').textContent = err.message;
  }
}

// Wire up static elements once
function bindStatic() {
  $('#tab-login').addEventListener('click', () => selectTab('login'));
  $('#tab-signup').addEventListener('click', () => selectTab('signup'));
  $('#login-form').addEventListener('submit', onLogin);
  $('#signup-form').addEventListener('submit', onSignup);
  $('#logout-btn').addEventListener('click', logout);

  $('#composer').addEventListener('submit', onCompose);
  $('#composer').addEventListener('change', (e) => {
    if (e.target.name === 'mood') paintComposer();
  });
  $('#search').addEventListener('input', (e) => {
    state.query = e.target.value;
    renderEntries();
  });
  document.querySelectorAll('.filters .chip').forEach((chip) =>
    chip.addEventListener('click', () => {
      state.filterMood = chip.dataset.mood;
      document.querySelectorAll('.filters .chip').forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
      renderEntries();
    })
  );

  $('#edit-form').addEventListener('submit', onEditSave);
  $('#edit-cancel').addEventListener('click', () => $('#edit-dlg').close());

  $('#prefs-form').addEventListener('submit', onPrefs);
  $('#city-form').addEventListener('submit', onCity);
  $('#password-form').addEventListener('submit', onPassword);
  $('#delete-form').addEventListener('submit', onDelete);
  $('#delete-form').elements.confirm.addEventListener('input', (e) => {
    $('#delete-form button').disabled = e.target.value !== 'DELETE';
  });

  window.addEventListener('hashchange', route);
}

init();
