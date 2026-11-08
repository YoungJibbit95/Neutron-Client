// CI-only diagnostic. No credentials, query parameters, response body or headers are logged.
// Only public, outbound HTTPS 5xx download targets are shown.
const https = require('node:https');
const originalRequest = https.request;
https.request = function tracedRequest(...args) {
  const request = originalRequest.apply(this, args);
  request.once('response', (response) => {
    if (!response.statusCode || response.statusCode < 500) return;
    try {
      const source = args[0];
      const options = typeof source === 'object' && !(source instanceof URL) ? source : args[1];
      const url = typeof source === 'string' || source instanceof URL ? new URL(source) : null;
      const hostname = url?.hostname || options?.hostname || options?.host;
      const pathname = url?.pathname || String(options?.path || '').split('?')[0];
      if (hostname && /^(?:github\.com|api\.github\.com|[^.]+\.githubusercontent\.com)$/.test(hostname)) {
        console.error('[neutron-download-diagnostic] HTTP ' + response.statusCode + ' ' + hostname + (pathname || '/'));
      }
    } catch { /* never interfere with application downloads */ }
  });
  return request;
};
