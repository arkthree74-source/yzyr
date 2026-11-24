window.__hwHarvest = function () {
  var KW = /作业|任务|测验|考试|实验|报告|论文|练习/;
  var DATE = /(\d{4}\s*[-\/年.]\s*\d{1,2}\s*[-\/月.]\s*\d{1,2}|\d{1,2}\s*[-\/月.]\s*\d{1,2}\s*日?)/;
  var STATE = /未完成|待提交|未提交|待批阅|进行中|未开始|截止|剩余|已过期|逾期/;
  var out = [];
  var seen = {};
  var nodes = document.querySelectorAll('li,tr,a,div,span,p,h1,h2,h3,h4,h5,section,article,td');
  var limit = Math.min(nodes.length, 6000);
  for (var i = 0; i < limit; i++) {
    var el = nodes[i];
    var txt = '';
    try { txt = (el.innerText || '').replace(/\s+/g, ' ').trim(); } catch (e) { continue; }
    if (!txt || txt.length < 4 || txt.length > 200) { continue; }
    if (!KW.test(txt)) { continue; }
    var hasDate = DATE.test(txt);
    var hasState = STATE.test(txt);
    if (!hasDate && !hasState) { continue; }
    if (el.querySelectorAll('*').length > 40) { continue; }
    var key = txt.slice(0, 80);
    if (seen[key]) { continue; }
    seen[key] = 1;
    var a = el.querySelector('a[href]');
    var href = '';
    if (a) { href = a.href; }
    else if (el.tagName === 'A') { href = el.href; }
    out.push({ t: txt, h: href || '' });
    if (out.length >= 200) { break; }
  }
  return out;
};