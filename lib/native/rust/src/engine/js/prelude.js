// Prelude installed into every override realm before the user script runs.
//
// Native globals provided by src/engine/js/natives.rs:
//   __yamlParseNative(text) -> json text     __yamlStringifyNative(json text) -> yaml text
//   __b64dNative(base64) -> text             __b64eNative(text) -> base64
//   __consoleLogNative(level, message)       __fetchNative(request json) -> response json
//   __encrypted ("true" | "false")           __overridePath / __overrideLogPath
const yaml = Object.freeze({
  parse(value) {
    return JSON.parse(__yamlParseNative(String(value)));
  },
  stringify(value) {
    return __yamlStringifyNative(JSON.stringify(value));
  }
});
const trimWrap = (value) => {
  if (typeof value !== "string") {
    return value;
  }
  if (value.startsWith("<") && value.endsWith(">")) {
    return value.slice(1, -1);
  }
  return value;
};
const isObject = (item) => item && typeof item === "object" && !Array.isArray(item);
const deepMerge = (target, other, isOverride = true) => {
  for (const key in other) {
    if (isObject(other[key])) {
      if (key.endsWith("!")) {
        const nextKey = trimWrap(key.slice(0, -1));
        target[nextKey] = other[key];
      } else {
        const nextKey = trimWrap(key);
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: {} });
        }
        deepMerge(target[nextKey], other[key], isOverride);
      }
    } else if (Array.isArray(other[key])) {
      if (isOverride && key.startsWith("+")) {
        const nextKey = trimWrap(key.slice(1));
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: [] });
        }
        target[nextKey] = [...other[key], ...target[nextKey]];
      } else if (isOverride && key.endsWith("+")) {
        const nextKey = trimWrap(key.slice(0, -1));
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: [] });
        }
        target[nextKey] = [...target[nextKey], ...other[key]];
      } else {
        const nextKey = trimWrap(key);
        Object.assign(target, { [nextKey]: [...other[key]] });
      }
    } else {
      Object.assign(target, { [key]: other[key] });
    }
  }
  return target;
};
const formatLogValue = (value) => {
  if (__encrypted === "true") {
    return "(redacted, encrypted profile)";
  }
  if (value instanceof Error) {
    return `${value.name}: ${value.message}`;
  }
  try {
    const serialized = JSON.stringify(value);
    const text = serialized === undefined ? String(value) : serialized;
    return text;
  } catch (error) {
    return String(value);
  }
};
const console = Object.freeze({
  log: (...args) => __consoleLogNative("log", args.map(formatLogValue).join(" ")),
  info: (...args) => __consoleLogNative("info", args.map(formatLogValue).join(" ")),
  error: (...args) => __consoleLogNative("error", args.map(formatLogValue).join(" ")),
  debug: (...args) => __consoleLogNative("debug", args.map(formatLogValue).join(" ")),
  warn: (...args) => __consoleLogNative("warn", args.map(formatLogValue).join(" "))
});
const normalizeFetchRequest = (input, init = undefined) => {
  const base = typeof input === "string" ? { url: input } : { ...(input || {}) };
  const requestInit = init ? { ...init } : {};
  const url = String(requestInit.url ?? base.url ?? "");
  if (!url) {
    throw new Error("fetch(url) requires a non-empty url");
  }
  const method = String(requestInit.method ?? base.method ?? "GET").toUpperCase();
  const headers = { ...(base.headers || {}), ...(requestInit.headers || {}) };
  const bodySource = requestInit.body !== undefined ? requestInit.body : base.body;
  const request = { url, method, headers };
  if (bodySource !== undefined) {
    request.body = typeof bodySource === "string" ? bodySource : JSON.stringify(bodySource);
  }
  return request;
};
const createFetchResponse = (payload) => {
  const headerEntries = Object.freeze({ ...(payload.headers || {}) });
  return Object.freeze({
    ok: Boolean(payload.ok),
    status: Number(payload.status || 0),
    statusText: String(payload.statusText || ""),
    url: String(payload.url || ""),
    headers: Object.freeze({
      ...headerEntries,
      get(name) {
        const key = String(name).toLowerCase();
        return headerEntries[key] ?? null;
      },
      has(name) {
        const key = String(name).toLowerCase();
        return key in headerEntries;
      },
      toJSON() {
        return { ...headerEntries };
      }
    }),
    async text() {
      return String(payload.body ?? "");
    },
    async json() {
      return JSON.parse(String(payload.body ?? ""));
    },
    async yaml() {
      return yaml.parse(String(payload.body ?? ""));
    }
  });
};
const fetch = async (input, init = undefined) => {
  const request = normalizeFetchRequest(input, init);
  const payload = JSON.parse(__fetchNative(JSON.stringify(request)));
  return createFetchResponse(payload);
};
const b64d = (value) => __b64dNative(String(value));
const createBuffer = (base64Value) => Object.freeze({
  __isYumeBuffer: true,
  __base64: String(base64Value),
  toString(encoding = "utf8") {
    const normalizedEncoding = String(encoding).toLowerCase();
    if (normalizedEncoding === "base64") {
      return this.__base64;
    }
    if (normalizedEncoding === "utf8" || normalizedEncoding === "utf-8") {
      return __b64dNative(this.__base64);
    }
    throw new Error(`Buffer.toString() unsupported encoding: ${encoding}`);
  },
  valueOf() {
    return this.toString("utf8");
  }
});
const Buffer = Object.freeze({
  from(value, encoding = "utf8") {
    if (value && value.__isYumeBuffer === true && typeof value.__base64 === "string") {
      return createBuffer(value.__base64);
    }
    const normalizedEncoding = String(encoding).toLowerCase();
    if (normalizedEncoding === "base64") {
      return createBuffer(String(value));
    }
    if (normalizedEncoding === "utf8" || normalizedEncoding === "utf-8") {
      return createBuffer(__b64eNative(String(value)));
    }
    throw new Error(`Buffer.from() unsupported encoding: ${encoding}`);
  },
  isBuffer(value) {
    return Boolean(value && value.__isYumeBuffer === true && typeof value.__base64 === "string");
  }
});
const b64e = (value) => Buffer.isBuffer(value) ? value.toString("base64") : __b64eNative(String(value));
const installBuiltin = (name, value) => {
  Object.defineProperty(globalThis, name, {
    value,
    writable: false,
    configurable: false,
    enumerable: false
  });
};
installBuiltin("yaml", yaml);
installBuiltin("deepMerge", deepMerge);
installBuiltin("b64d", b64d);
installBuiltin("b64e", b64e);
installBuiltin("Buffer", Buffer);
installBuiltin("console", console);
installBuiltin("fetch", fetch);
