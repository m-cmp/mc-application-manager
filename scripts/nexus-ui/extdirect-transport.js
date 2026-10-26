// Keep the standard ExtDirect provider, session and CSRF handling intact.
// A per-page query prevents an intermediary from reusing a stale endpoint route.
;(function () {
  var api = NX.direct.api.REMOTING_API;
  var endpoint = new URL(api.url, window.location.href);
  endpoint.searchParams.set('_mcmpTransport', String(Date.now()));
  api.url = endpoint.href;
})();
