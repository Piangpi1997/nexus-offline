/* NEXUS offline relay engine — isolated simulation adapter; NOT authenticated or production wired. */
(function(root,factory){
 const api=factory(typeof module==='object'&&module.exports?require('./mesh-protocol.js'):root.NexusMeshProtocol);
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.NexusMeshRelay=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(protocol){
 'use strict';
 if(!protocol)throw Error('MESH_PROTOCOL_REQUIRED');
 function createRelay({nodeId,capacity=128,now=()=>Date.now()}={}){
  if(typeof nodeId!=='string'||nodeId.length<8||!Number.isInteger(capacity)||capacity<1||capacity>10000)throw Error('INVALID_RELAY_CONFIG');
  const queue=new Map(),receipts=new Set();
  function purge(){
   const t=now();
   for(const [id,e] of queue)if(e.expiresAt<=t)queue.delete(id);
  }
  function receive(envelope){
   purge();
   const error=protocol.validate(envelope,now());
   if(error)return {ok:false,error};
   if(queue.has(envelope.messageId)||receipts.has(envelope.messageId))return {ok:false,error:'DUPLICATE'};
   if(envelope.senderId===nodeId)return {ok:false,error:'LOOPBACK'};
   if(queue.size>=capacity)return {ok:false,error:'QUEUE_FULL'};
   // Shape validation only. Do not attach to untrusted peers before signature verification.
   queue.set(envelope.messageId,Object.freeze({...envelope}));
   return {ok:true,status:envelope.recipientId===nodeId?'RECEIVED':'QUEUED'};
  }
  function offer(peerId,limit=32){
   purge();
   if(typeof peerId!=='string'||peerId.length<8||!Number.isInteger(limit)||limit<1||limit>128)throw Error('INVALID_OFFER');
   return [...queue.values()].filter(e=>e.senderId!==peerId&&e.hopsRemaining>0).slice(0,limit).map(e=>e.messageId);
  }
  function forward(messageId,peerId){
   purge();
   const e=queue.get(messageId);
   if(!e)return {ok:false,error:'NOT_FOUND'};
   if(peerId===nodeId||peerId===e.senderId)return {ok:false,error:'LOOPBACK'};
   return protocol.nextHop(e,now());
  }
  function acknowledge(messageId,{destinationVerified=false}={}){
   if(!queue.has(messageId))return false;
   if(destinationVerified){queue.delete(messageId);receipts.add(messageId);}
   return true; // hop-level acknowledgement does not delete pending delivery
  }
  function snapshot(){purge();return [...queue.values()].map(e=>({...e}));}
  return Object.freeze({receive,offer,forward,acknowledge,snapshot});
 }
 return Object.freeze({createRelay});
});
