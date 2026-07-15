// ── PRIVACY CACHE ──────────────────────────────────────
// Per-device privacy mode for sensitive app data. When disabled, the app
// still syncs through Firestore but skips/restores no plaintext localStorage
// cache for financial/profile data on this device.
export const SENSITIVE_CACHE_PREF_KEY = 'mxSensitiveCache';

export const SENSITIVE_CACHE_NAMES = ['shifts','tx','recurring','debt','cfg'];

export function isSensitiveLocalCacheEnabled(){
  try{ return localStorage.getItem(SENSITIVE_CACHE_PREF_KEY) !== '0'; }
  catch(e){ return true; }
}

export function setSensitiveLocalCacheEnabled(enabled){
  try{ localStorage.setItem(SENSITIVE_CACHE_PREF_KEY, enabled ? '1' : '0'); }catch(e){}
}

export function getCacheItem(key){
  if(!key || !isSensitiveLocalCacheEnabled()) return null;
  try{ return localStorage.getItem(key); }catch(e){ return null; }
}

export function setCacheItem(key, value){
  if(!key) return;
  try{
    if(!isSensitiveLocalCacheEnabled()) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  }catch(e){}
}

export function removeCacheItem(key){
  if(!key) return;
  try{ localStorage.removeItem(key); }catch(e){}
}

function cacheKeyFor(uid, name, profileId){
  if(!uid) return null;
  return profileId && profileId!=='default' ? `mx_${name}_${uid}_${profileId}` : `mx_${name}_${uid}`;
}

export function sensitiveProfileIds(profilesMeta){
  const ids=['default'];
  const list=profilesMeta&&Array.isArray(profilesMeta.list)?profilesMeta.list:[];
  list.forEach(p=>{ if(p&&p.id&&!ids.includes(p.id)) ids.push(p.id); });
  return ids;
}

export function clearSensitiveLocalCacheForAccount(uid, profilesMeta){
  sensitiveProfileIds(profilesMeta).forEach(profileId=>{
    SENSITIVE_CACHE_NAMES.forEach(name=>removeCacheItem(cacheKeyFor(uid, name, profileId)));
  });
}

export function clearSensitiveLocalCacheForUser(lsKeyForName){
  SENSITIVE_CACHE_NAMES.forEach(name=>{
    try{ removeCacheItem(lsKeyForName(name)); }catch(e){}
  });
}
