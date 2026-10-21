package com.kigtts.next.media

internal val WEB_DEVICE_SCRIPT = """
(()=>{
  const native=window.KigttsDeviceConsent;if(!native)return;
  let serial=0;const pending=new Map();
  native.onmessage=e=>{try{const r=JSON.parse(e.data),p=pending.get(r.id);if(p){pending.delete(r.id);p(r);}}catch(e){}};
  function request(operation,extra={}){return new Promise(resolve=>{const id=String(++serial);pending.set(id,resolve);native.postMessage(JSON.stringify({id,operation,...extra}));setTimeout(()=>{if(pending.delete(id))resolve({ok:false,error:'授权超时'});},120000);});}
  let motion;
  function requestMotionPermission(){return (motion||(motion=request('motion'))).then(r=>r.ok?'granted':'denied');}
  const add=EventTarget.prototype.addEventListener,remove=EventTarget.prototype.removeEventListener,waiting=new Set();
  EventTarget.prototype.addEventListener=function(type,listener,options){if(this===window&&['devicemotion','deviceorientation'].includes(type)){const entry={type,listener,options};waiting.add(entry);requestMotionPermission().then(result=>{if(result==='granted'&&waiting.has(entry))add.call(window,type,listener,options);});return;}return add.call(this,type,listener,options);};
  EventTarget.prototype.removeEventListener=function(type,listener,options){if(this===window)for(const entry of waiting)if(entry.type===type&&entry.listener===listener)waiting.delete(entry);return remove.call(this,type,listener,options);};
  for(const type of ['devicemotion','deviceorientation']){let current=null;try{Object.defineProperty(window,'on'+type,{configurable:true,get:()=>current,set:listener=>{if(current)window.removeEventListener(type,current);current=listener;if(listener)window.addEventListener(type,listener);}});}catch(e){}}
  for(const kind of ['DeviceMotionEvent','DeviceOrientationEvent']){try{if(window[kind])Object.defineProperty(window[kind],'requestPermission',{value:requestMotionPermission,configurable:true});}catch(e){}}
  const streams=new Set();if(navigator.mediaDevices?.getUserMedia){const get=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);navigator.mediaDevices.getUserMedia=constraints=>get(constraints).then(stream=>{streams.add(stream);return stream;});}
  Object.defineProperty(window,'__kigttsStopDeviceTracks',{value:()=>{for(const stream of streams)for(const track of stream.getTracks())track.stop();streams.clear();}});
  async function download(value,name='download',mime='application/octet-stream'){
    if(typeof value==='string'&&/^https:\/\//.test(value))return request('download-url',{url:value,name,mime});
    const blob=value instanceof Blob?value:await fetch(value).then(r=>r.blob());if(blob.size>8*1024*1024)throw Error('生成文件超过8MiB');
    const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=reject;reader.readAsDataURL(blob);});
    return request('download-data',{data,name,mime:blob.type||mime});
  }
  add.call(document,'click',e=>{const a=e.target.closest?.('a[download]');if(a&&/^(blob:|data:)/.test(a.href)){e.preventDefault();download(a.href,a.download||'download').catch(console.error);}},true);
  Object.defineProperty(window,'KigttsDevices',{value:Object.freeze({requestMotionPermission,download})});
  request('document-start');
})();
""".trimIndent()
