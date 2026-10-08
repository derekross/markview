/*
 * Markview offline render engine. Runs in a hidden WebView (Chromium).
 *
 *   MV.render(id, kind, source, options)  kind: "math-inline" | "math-display" | "mermaid"
 *
 * Results are delivered to the host through window.MarkviewBridge.onResult(id, ok, payload, width, height):
 *   math    -> payload is an SVG string (glyphs as paths, color = currentColor)
 *   mermaid -> payload is a PNG data URL; width/height are the diagram size in CSS px
 * Requests are serialized because mermaid.render is not re-entrant.
 */
(function () {
  'use strict';

  var bridge = window.MarkviewBridge || {
    onResult: function () {},
    onReady: function () {},
  };
  var queue = Promise.resolve();
  var counter = 0;

  function mathReady() {
    return window.MathJax && MathJax.startup && MathJax.startup.promise
      ? MathJax.startup.promise
      : Promise.reject(new Error('MathJax not loaded'));
  }

  function renderMath(source, display) {
    return mathReady().then(function () {
      var container = MathJax.tex2svg(source, { display: display });
      var svg = container.querySelector('svg');
      if (!svg) throw new Error('No SVG produced');
      var error = svg.querySelector('[data-mml-node="merror"]');
      if (error) throw new Error(error.getAttribute('data-mjx-error') || error.textContent || 'TeX error');
      svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      return { payload: svg.outerHTML, width: 0, height: 0 };
    });
  }

  function cleanup(id) {
    // mermaid leaves temporary nodes behind when rendering fails.
    ['d' + id, id].forEach(function (key) {
      var node = document.getElementById(key);
      if (node && node.parentNode) node.parentNode.removeChild(node);
    });
  }

  function renderMermaid(source, options) {
    var id = 'mv' + counter++;
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'strict',
      theme: 'base',
      darkMode: !!options.dark,
      fontFamily: options.fontFamily || 'sans-serif',
      themeVariables: options.themeVariables || {},
      htmlLabels: false,
      flowchart: { htmlLabels: false, useMaxWidth: false },
      sequence: { useMaxWidth: false },
      gantt: { useMaxWidth: false },
      class: { htmlLabels: false, useMaxWidth: false },
      state: { useMaxWidth: false },
      er: { useMaxWidth: false },
      journey: { useMaxWidth: false },
      pie: { useMaxWidth: false },
      mindmap: { useMaxWidth: false },
      timeline: { useMaxWidth: false },
    });
    return mermaid.render(id, source).then(function (result) {
      cleanup(id);
      return rasterize(result.svg, options.scale || 2);
    }, function (error) {
      cleanup(id);
      throw error;
    });
  }

  function svgSize(svgText) {
    var doc = new DOMParser().parseFromString(svgText, 'image/svg+xml');
    var svg = doc.documentElement;
    var box = (svg.getAttribute('viewBox') || '').split(/[\s,]+/).map(Number);
    var width = box.length === 4 ? box[2] : parseFloat(svg.getAttribute('width'));
    var height = box.length === 4 ? box[3] : parseFloat(svg.getAttribute('height'));
    if (!(width > 0 && height > 0)) throw new Error('Diagram has no size');
    svg.setAttribute('width', String(width));
    svg.setAttribute('height', String(height));
    svg.removeAttribute('style');
    return { svg: new XMLSerializer().serializeToString(svg), width: width, height: height };
  }

  function rasterize(svgText, scale) {
    var sized = svgSize(svgText);
    // Stay under typical GPU/canvas limits (~16 MP).
    var maxPixels = 16000000;
    var pixels = sized.width * sized.height * scale * scale;
    if (pixels > maxPixels) scale = Math.sqrt(maxPixels / (sized.width * sized.height));
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () {
        try {
          var canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(sized.width * scale));
          canvas.height = Math.max(1, Math.round(sized.height * scale));
          var ctx = canvas.getContext('2d');
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
          resolve({ payload: canvas.toDataURL('image/png'), width: sized.width, height: sized.height });
        } catch (e) {
          reject(e);
        }
      };
      img.onerror = function () { reject(new Error('Could not rasterize diagram')); };
      img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(sized.svg);
    });
  }

  function render(id, kind, source, options) {
    queue = queue.then(function () {
      var work;
      if (kind === 'math-inline') work = renderMath(source, false);
      else if (kind === 'math-display') work = renderMath(source, true);
      else if (kind === 'mermaid') work = renderMermaid(source, options || {});
      else work = Promise.reject(new Error('Unknown kind ' + kind));
      return work.then(function (r) {
        bridge.onResult(id, true, r.payload, r.width, r.height);
      }, function (e) {
        bridge.onResult(id, false, String((e && e.message) || e), 0, 0);
      });
    });
  }

  window.MV = { render: render };

  mathReady().then(function () { bridge.onReady(); }, function () { bridge.onReady(); });
})();
