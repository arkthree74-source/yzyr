// Probe: list every stat2-ans / chaoxing API the 学习规划助手 panel requested.
(function () {
  function done(v) {
    window.__probe = (typeof v === 'string') ? v : JSON.stringify(v, null, 1);
  }
  var out = { href: location.href, apis: [], all: [] };
  try {
    var entries = performance.getEntriesByType('resource') || [];
    for (var i = 0; i < entries.length; i++) {
      var u = entries[i].name;
      out.all.push(u);
      if (/\/stat2\/|chaoxing/i.test(u) && !/\.(js|css|png|jpg|jpeg|svg|gif|woff2?|ttf|ico)(\?|$)/i.test(u)) {
        out.apis.push({ url: u, type: entries[i].initiatorType });
      }
    }
    out.total = out.all.length;
    out.all = out.all.slice(0, 0);
  } catch (e) {
    out.error = String(e);
  }
  try {
    out.body = (document.body.innerText || '').slice(0, 1500);
  } catch (e2) {
  }
  done(out);
})();