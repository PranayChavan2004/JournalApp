// Thin fetch wrapper for the Journal API.
// Authentication is a HttpOnly cookie that the browser attaches by itself, so this file never
// sees or stores a token. The X-Requested-With header is required by the server for every
// request that changes data (it is what blocks cross-site request forgery).

export class ApiError extends Error {
  constructor(status, message, errors) {
    super(message);
    this.status = status;
    this.errors = errors || null;
  }
}

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (res.status === 204) return null;

  let data = null;
  try {
    data = await res.json();
  } catch {
    /* empty or non-JSON body */
  }

  if (!res.ok) {
    // a 401 anywhere except the login form itself means the session ended
    if (res.status === 401 && !path.startsWith('/public/')) onUnauthorized();
    throw new ApiError(res.status, (data && data.message) || 'Something went wrong. Please try again.', data && data.errors);
  }
  return data;
}
