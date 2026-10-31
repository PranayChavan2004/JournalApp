// Small DOM helpers. Everything the user typed is inserted as a text node (never as HTML),
// so a journal entry containing "<script>" is shown as plain text and cannot run.

export const MOODS = [
  { value: 'HAPPY', label: 'Happy' },
  { value: 'SAD', label: 'Sad' },
  { value: 'ANGRY', label: 'Angry' },
  { value: 'ANXIOUS', label: 'Anxious' },
];

export function moodLabel(value) {
  const m = MOODS.find((x) => x.value === value);
  return m ? m.label : '';
}

/** h('div', {class: 'x', onClick: fn}, 'text', childNode) */
export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, String(value));
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

let toastTimer;
export function toast(message, kind = 'ok') {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = 'toast show' + (kind === 'error' ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

export function setError(form, message) {
  const el = form.querySelector('.form-error');
  if (el) el.textContent = message || '';
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });
export const formatTime = (d) => timeFmt.format(d);

export function dayKey(d) {
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
}

export function dayLabel(d) {
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (dayKey(d) === dayKey(today)) return 'Today';
  if (dayKey(d) === dayKey(yesterday)) return 'Yesterday';
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: d.getFullYear() === today.getFullYear() ? undefined : 'numeric',
  }).format(d);
}

export function shortDay(d) {
  return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(d);
}
