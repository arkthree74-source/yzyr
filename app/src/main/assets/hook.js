(function () {
  if (window.__hwHooked) { return; }
  window.__hwHooked = true;
  window.__hwCapture = [];

  function push(url, text, headers) {
    try {
      if (!text) { return; }
      if (typeof text !== 'string') { return; }
      if (text.length > 300000) { text = text.slice(0, 300000); }
      var arr = window.__hwCapture;
      var item = { u: String(url || ''), t: text, ts: Date.now() };
      if (headers && Object.keys(headers).length) { item.h = headers; }
      arr.push(item);
      if (arr.length > 100) { arr.shift(); }
    } catch (e) { }
  }

  try {
    var XO = XMLHttpRequest.prototype.open;
    var XS = XMLHttpRequest.prototype.send;
    var XH = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.open = function (m, u) {
      this.__hwM = m;
      this.__hwU = u;
      this.__hwH = {};
      return XO.apply(this, arguments);
    };
    XMLHttpRequest.prototype.setRequestHeader = function (k, v) {
      try { this.__hwH[String(k)] = String(v); } catch (e) { }
      return XH.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var x = this;
      try {
        x.addEventListener('load', function () {
          try { push(x.__hwU, x.responseText, x.__hwH); } catch (e) { }
        });
      } catch (e) { }
      return XS.apply(this, arguments);
    };
  } catch (e) { }

  try {
    if (window.fetch) {
      var of = window.fetch;
      window.fetch = function (input, init) {
        var u = (typeof input === 'string') ? input : (input && input.url);
        var h = null;
        try {
          if (init && init.headers) {
            h = {};
            if (typeof init.headers.forEach === 'function') {
              init.headers.forEach(function (v, k) { h[k] = v; });
            } else if (Array.isArray(init.headers)) {
              init.headers.forEach(function (kv) { h[kv[0]] = kv[1]; });
            } else {
              Object.keys(init.headers).forEach(function (k) { h[k] = init.headers[k]; });
            }
          }
        } catch (e) { }
        var p = of.apply(this, arguments);
        try {
          p.then(function (r) {
            try {
              r.clone().text().then(function (t) { push(u, t, h); }).catch(function () { });
            } catch (e) { }
          }).catch(function () { });
        } catch (e) { }
        return p;
      };
    }
  } catch (e) { }
})();