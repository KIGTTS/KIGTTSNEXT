package com.kigtts.next.media

import org.json.JSONObject

internal object BrowserDriverScripts {
    val state = """(()=>{
      function selector(e){if(e.id&&document.querySelectorAll('#'+CSS.escape(e.id)).length===1)return '#'+CSS.escape(e.id);let p=[];while(e&&e!==document.body&&p.length<7){let n=1,s=e;while(s=s.previousElementSibling)if(s.tagName===e.tagName)n++;p.unshift(e.tagName.toLowerCase()+':nth-of-type('+n+')');e=e.parentElement;}return 'body>'+p.join('>');}
      const v=window.visualViewport,w=v?.width||document.documentElement.clientWidth,h=v?.height||document.documentElement.clientHeight,left=v?.offsetLeft||0,top=v?.offsetTop||0;
      const elements=Array.from(document.querySelectorAll('button,a,input,textarea,select,[role=button],[contenteditable=true],canvas')).filter(e=>{const r=e.getBoundingClientRect();return r.width&&r.height&&r.bottom>top&&r.top<top+h&&r.right>left&&r.left<left+w;}).slice(0,100).map(e=>{const r=e.getBoundingClientRect();return {selector:selector(e),tag:e.tagName.toLowerCase(),text:(e.getAttribute('aria-label')||e.innerText||e.getAttribute('placeholder')||'').slice(0,160),x:r.x-left,y:r.y-top,width:r.width,height:r.height};});
      return JSON.stringify({title:document.title,url:location.origin+location.pathname,text:(document.body?.innerText||'').slice(0,10000),elements,width:w,height:h,layoutWidth:innerWidth,devicePixelRatio,visualScale:v?.scale||1,ready:document.readyState});})()"""
    fun action(operation: String, selector: String, text: String) = """(()=>{try{
      const q=${JSONObject.quote(selector)},matches=document.querySelectorAll(q);if(matches.length!==1)return JSON.stringify({ok:false,error:'选择器需要唯一匹配'});
      const e=matches[0],r=e.getBoundingClientRect();if(!r.width||!r.height)return JSON.stringify({ok:false,error:'元素不可见'});
      e.scrollIntoView({block:'nearest',inline:'nearest'});
      if(${JSONObject.quote(operation)}==='click'){const p=e.getBoundingClientRect(),v=window.visualViewport;return JSON.stringify({ok:true,x:(p.x+p.width/2-(v?.offsetLeft||0))/(v?.width||document.documentElement.clientWidth),y:(p.y+p.height/2-(v?.offsetTop||0))/(v?.height||document.documentElement.clientHeight)});}else{
        const v=${JSONObject.quote(text)};e.focus();
        if(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement){const p=e instanceof HTMLInputElement?HTMLInputElement.prototype:HTMLTextAreaElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,v);}
        else if(e.isContentEditable)e.textContent=v;else return JSON.stringify({ok:false,error:'目标不是输入控件'});
        e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}
      return JSON.stringify({ok:true});}catch(e){return JSON.stringify({ok:false,error:String(e).slice(0,300)});}})()"""
}
