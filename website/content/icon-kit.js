(() => {
  if (window.JSZip) {
    window.dispatchEvent(new Event("icon-kit-jszip-ready"));
    return;
  }

  const script = document.createElement("script");
  script.src = "https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js";
  script.onload = () => window.dispatchEvent(new Event("icon-kit-jszip-ready"));
  document.head.appendChild(script);
})();
