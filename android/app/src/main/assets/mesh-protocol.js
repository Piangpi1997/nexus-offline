/* NEXUS Mesh v1 — protocol-only, no network or crypto claims. */
(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.NexusMeshProtocol=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const MAX_HOPS=8,MAX_AGE_MS=7*24*60*60*1000,MAX_ID=128,MAX_BODY=256*1024;
  const idOk=x=>typeof x==='string'&&x.length>=8&&x.length<=MAX_ID&&/^[A-Za-z0-9_-]+$/.test(x);
  function validate(e,now=Date.now()){
    if(!e||typeof e!=='object'||Array.isArray(e))return 'INVALID_ENVELOPE';
    if(e.version!==1)return 'UNSUPPORTED_VERSION';
    if(!idOk(e.messageId)||!idOk(e.senderId)||!idOk(e.recipientId))return 'INVALID_ID';
    if(!Number.isSafeInteger(e.createdAt)||!Number.isSafeInteger(e.expiresAt))return 'INVALID_TIME';
    if(e.createdAt>now+60000||e.expiresAt<=e.createdAt||e.expiresAt-e.createdAt>MAX_AGE_MS)return 'INVALID_TIME';
    if(e.expiresAt<=now)return 'EXPIRED';
    if(!Number.isInteger(e.hopsRemaining)||e.hopsRemaining<0||e.hopsRemaining>MAX_HOPS)return 'INVALID_HOPS';
    if(typeof e.ciphertext!=='string'||e.ciphertext.length===0||e.ciphertext.length>MAX_BODY*2)return 'INVALID_CIPHERTEXT';
    if(typeof e.signature!=='string'||e.signature.length<16||e.signature.length>1024)return 'INVALID_SIGNATURE';
    return null;
  }
  function nextHop(e,now=Date.now()){
    const error=validate(e,now);if(error)return {ok:false,error};
    if(e.hopsRemaining===0)return {ok:false,error:'HOP_LIMIT'};
    return {ok:true,envelope:{...e,hopsRemaining:e.hopsRemaining-1}};
  }
  function createSeenCache(limit=4096){
    if(!Number.isInteger(limit)||limit<1||limit>100000)throw Error('INVALID_CACHE_LIMIT');
    const seen=new Map();
    return {
      has(id,now=Date.now()){const expiry=seen.get(id);if(expiry===undefined)return false;if(expiry<=now){seen.delete(id);return false;}return true;},
      mark(id,expiry,now=Date.now()){if(!idOk(id)||!Number.isSafeInteger(expiry)||expiry<=now)return false; if(seen.has(id))seen.delete(id);seen.set(id,expiry);while(seen.size>limit)seen.delete(seen.keys().next().value);return true;},
      size(){return seen.size;}
    };
  }
  function shouldForward(e,seen,now=Date.now()){
    const error=validate(e,now);if(error)return {ok:false,error};
    if(seen.has(e.messageId,now))return {ok:false,error:'DUPLICATE'};
    return nextHop(e,now);
  }
  return Object.freeze({validate,nextHop,createSeenCache,shouldForward,MAX_HOPS,MAX_AGE_MS});
});
