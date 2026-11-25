// Parity check for the hand-maintained copies of the same logical lists:
// hotel-web-fe runtimeApi.ts ROOT_API_PREFIXES, vite.config.ts PROXY_PREFIXES,
// deploy/Caddyfile @backend prefixes, src-tauri commands.rs PROD/DEV_ALLOWED_ORIGINS,
// and capabilities/dev.json remote.urls. Run: bun run test:scripts (or `bun test scripts/`).
import { describe, expect, test } from 'bun:test';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const read = (rel) => readFileSync(join(repoRoot, rel), 'utf8');

// Extracts the items of a flat single-quoted array literal, e.g.
// `const NAME = ['a', 'b'];`. Throws when the literal is absent or yields no
// items, so a moved/renamed constant — or a reformat to double quotes — fails
// loudly instead of passing on two empty lists.
const listLiteral = (source, name) => {
  const match = source.match(new RegExp(`${name}\\s*=\\s*\\[([^\\]]+)\\]`));
  if (!match) throw new Error(`${name} array literal not found`);
  const items = [...match[1].matchAll(/'([^']+)'/g)].map((m) => m[1]);
  if (items.length === 0) {
    throw new Error(`${name} literal found but no single-quoted items extracted`);
  }
  return items;
};

const originsConstant = (name) => {
  const commands = read('hotel-desktop/src-tauri/src/commands.rs');
  const match = commands.match(new RegExp(`const ${name}:\\s*&str\\s*=\\s*"([^"]+)"`));
  if (!match) throw new Error(`${name} constant not found in commands.rs`);
  return match[1].split(',').map((s) => s.trim());
};

const capabilityRemoteOrigins = (rel) => {
  const caps = JSON.parse(read(rel));
  return caps.remote?.urls ? caps.remote.urls.map((u) => u.replace(/\/\*$/, '')) : [];
};

// The origin `tauri dev` actually loads in the webview — the single most
// important entry in both lists.
const devUrlOrigin = () => {
  const conf = JSON.parse(read('hotel-desktop/src-tauri/tauri.conf.json'));
  if (!conf.build?.devUrl) throw new Error('build.devUrl not found in tauri.conf.json');
  return new URL(conf.build.devUrl).origin;
};

// Scope is every http(s) origin — remote.urls may legitimately carry
// non-loopback urls too. Only the tauri webview origins are excluded: they are
// http-shaped on some platforms but app-internal, never capability remote urls.
const isRemoteHttpOrigin = (origin) => /^https?:\/\//.test(origin) && !origin.includes('tauri');

describe('origin/proxy parity', () => {
  test('runtimeApi ROOT_API_PREFIXES == vite PROXY_PREFIXES (sans leading /)', () => {
    const root = listLiteral(read('hotel-web-fe/src/desktop/runtimeApi.ts'), 'ROOT_API_PREFIXES');
    const proxy = listLiteral(read('hotel-web-fe/vite.config.ts'), 'PROXY_PREFIXES').map((p) =>
      p.replace(/^\//, ''),
    );
    expect([...root].sort()).toEqual([...proxy].sort());
  });

  test('deploy/Caddyfile @backend matches vite PROXY_PREFIXES', () => {
    const caddy = read('deploy/Caddyfile');
    const match = caddy.match(/@backend path\s+([^\n]+)/);
    if (!match) throw new Error('@backend path directive not found in deploy/Caddyfile');
    const caddyPrefixes = match[1]
      .trim()
      .split(/\s+/)
      .filter((p) => !/^\/(?:api|uploads|ws)$/.test(p))
      .map((p) => (p === '/hotel.*' ? '/hotel.' : p.replace(/\/\*$/, '')));
    const proxy = listLiteral(read('hotel-web-fe/vite.config.ts'), 'PROXY_PREFIXES');
    expect([...new Set(caddyPrefixes)].sort()).toEqual([...proxy].sort());
  });

  test('PROD_ALLOWED_ORIGINS includes only tauri webview origins and no remote http origins', () => {
    const prodOrigins = originsConstant('PROD_ALLOWED_ORIGINS');
    expect(prodOrigins).toContain('tauri://localhost');
    expect(prodOrigins).toContain('http://tauri.localhost');
    expect(prodOrigins.filter(isRemoteHttpOrigin)).toEqual([]);

    const defaultCaps = JSON.parse(read('hotel-desktop/src-tauri/capabilities/default.json'));
    expect(defaultCaps.remote).toBeUndefined();

    const prodConf = JSON.parse(read('hotel-desktop/src-tauri/tauri.conf.json'));
    expect(prodConf.app?.security?.capabilities).toEqual(['default']);
  });

  test('dev http origins in DEV_ALLOWED_ORIGINS == capabilities/dev.json remote.urls (sans /*)', () => {
    const devOrigins = originsConstant('DEV_ALLOWED_ORIGINS');
    expect(devOrigins).toContain('tauri://localhost');
    expect(devOrigins).toContain('http://tauri.localhost');

    const devConf = JSON.parse(read('hotel-desktop/src-tauri/tauri.dev.conf.json'));
    expect(devConf.app?.security?.capabilities).toEqual(['default', 'dev']);

    const origins = devOrigins.filter(isRemoteHttpOrigin).sort();
    const remote = capabilityRemoteOrigins('hotel-desktop/src-tauri/capabilities/dev.json')
      .filter(isRemoteHttpOrigin)
      .sort();
    const devOrigin = devUrlOrigin();
    expect(origins).toContain(devOrigin);
    expect(remote).toContain(devOrigin);
    expect(origins).toEqual(remote);
  });

  test('build-backend-sidecar.mjs hashes patch manifest.tsv and proto/', () => {
    const script = read('hotel-desktop/scripts/build-backend-sidecar.mjs');
    expect(script).toContain("'manifest.tsv'");
    expect(script).toContain("'proto'");
  });

  test('package-portable.mjs stages sidecar under stripped runtime name', () => {
    const script = read('hotel-desktop/scripts/package-portable.mjs');
    expect(script).toContain('const runtimeSidecarName = `hotel-app-be${exeSuffix}`');
    expect(script).toContain('[stagingDir, runtimeSidecarName]');
  });
});
