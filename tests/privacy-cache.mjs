// Unit tests for js/privacy-cache.js. Runs in plain Node with a tiny
// localStorage mock so the browser-facing cache helper can be tested without
// Playwright or Firebase.
import assert from 'node:assert/strict';

const store = new Map();
globalThis.localStorage = {
  getItem(key){ return store.has(String(key)) ? store.get(String(key)) : null; },
  setItem(key, value){ store.set(String(key), String(value)); },
  removeItem(key){ store.delete(String(key)); },
  clear(){ store.clear(); },
  get length(){ return store.size; },
  key(i){ return Array.from(store.keys())[i] ?? null; },
};

const cache = await import('../js/privacy-cache.js');

let passed = 0;
function test(name, fn){
  localStorage.clear();
  fn();
  passed++;
  console.log(`[ok] ${name}`);
}

test('defaults to enabled and reads/writes cache values', () => {
  assert.equal(cache.isSensitiveLocalCacheEnabled(), true);
  cache.setCacheItem('mx_tx_u1', '[1]');
  assert.equal(cache.getCacheItem('mx_tx_u1'), '[1]');
});

test('disabled mode skips reads and removes attempted writes', () => {
  localStorage.setItem('mx_tx_u1', '[old]');
  cache.setSensitiveLocalCacheEnabled(false);
  assert.equal(cache.getCacheItem('mx_tx_u1'), null);
  cache.setCacheItem('mx_tx_u1', '[new]');
  assert.equal(localStorage.getItem('mx_tx_u1'), null);
});

test('clearSensitiveLocalCacheForAccount clears default and named profiles only for sensitive names', () => {
  const profileIds = ['default', 'profile_a', 'profile_b'];
  for(const profileId of profileIds){
    const suffix = profileId === 'default' ? '' : `_${profileId}`;
    for(const name of cache.SENSITIVE_CACHE_NAMES){
      localStorage.setItem(`mx_${name}_u1${suffix}`, `${name}:${profileId}`);
    }
    localStorage.setItem(`mx_shopping_u1${suffix}`, `shopping:${profileId}`);
  }
  localStorage.setItem('mx_tx_other_user', 'keep');

  cache.clearSensitiveLocalCacheForAccount('u1', {list:[{id:'profile_a'}, {id:'profile_b'}]});

  for(const profileId of profileIds){
    const suffix = profileId === 'default' ? '' : `_${profileId}`;
    for(const name of cache.SENSITIVE_CACHE_NAMES){
      assert.equal(localStorage.getItem(`mx_${name}_u1${suffix}`), null, `${name}/${profileId} should be cleared`);
    }
    assert.equal(localStorage.getItem(`mx_shopping_u1${suffix}`), `shopping:${profileId}`);
  }
  assert.equal(localStorage.getItem('mx_tx_other_user'), 'keep');
});

test('clearSensitiveLocalCacheForAccount also clears cache for a profile no longer in profilesMeta.list', () => {
  // deleteProfile() only filters the metadata list - it doesn't sweep the
  // deleted profile's own localStorage cache, so this must still get swept
  // even though 'profile_deleted' isn't in the profilesMeta passed in.
  for(const name of cache.SENSITIVE_CACHE_NAMES){
    localStorage.setItem(`mx_${name}_u1_profile_deleted`, `${name}:orphan`);
  }
  localStorage.setItem('mx_tx_u1', 'default:keep-then-clear');
  localStorage.setItem('mx_tx_other_user', 'keep');
  localStorage.setItem('mx_tx_other_user_profile_deleted', 'keep');

  cache.clearSensitiveLocalCacheForAccount('u1', {list:[]});

  for(const name of cache.SENSITIVE_CACHE_NAMES){
    assert.equal(localStorage.getItem(`mx_${name}_u1_profile_deleted`), null, `${name}/profile_deleted should be cleared even though it's not in profilesMeta`);
  }
  assert.equal(localStorage.getItem('mx_tx_u1'), null);
  assert.equal(localStorage.getItem('mx_tx_other_user'), 'keep');
  assert.equal(localStorage.getItem('mx_tx_other_user_profile_deleted'), 'keep');
});

console.log(`\n${passed} privacy-cache test(s) passed`);
