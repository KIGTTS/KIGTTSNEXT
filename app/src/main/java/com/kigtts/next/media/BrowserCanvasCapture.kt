package com.kigtts.next.media

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay

/** Software WebView.draw omits GPU canvases. Read their real frame only in inspection views. */
internal object BrowserCanvasCapture {
    internal val script = """
                (()=>{const original=HTMLCanvasElement.prototype.getContext,canvases=new Set();
                  window.__kigttsCanvasState={painted:0,last:0};
                  const watched=new WeakSet();
                  HTMLCanvasElement.prototype.getContext=function(type,attributes){
                    const gpu=/^(webgl2?|experimental-webgl)$/.test(type);
                    const result=original.call(this,type,gpu?{...attributes,preserveDrawingBuffer:true}:attributes);
                    if(result){canvases.add(this);if(!watched.has(result)){watched.add(result);
                      for(const method of (gpu?['drawArrays','drawElements','clear']:['fillRect','strokeRect','fill','stroke','fillText','strokeText','drawImage','putImageData','clearRect'])){
                        const draw=result[method];if(typeof draw!=='function')continue;
                        try{result[method]=function(...args){const value=draw.apply(this,args);window.__kigttsCanvasState.painted++;window.__kigttsCanvasState.last=performance.now();return value;};}catch(e){}
                      }
                    }}return result;
                  };
                  let frames=[];
                  window.__kigttsCaptureCanvas=(restore)=>{
                    for(const i of frames)i.remove();frames=[];if(restore)return '{}';
                    const errors=[];
                    for(const c of new Set([...canvases,...document.querySelectorAll('canvas')])){if(!c.isConnected)continue;const r=c.getBoundingClientRect();
                      if(!r.width||!r.height)continue;
                      try{if(c.width*c.height>16000000)throw Error('Canvas exceeds capture limit');
                        const image=new Image();image.dataset.kigttsCanvasFrame='true';image.src=c.toDataURL('image/png');
                        image.style.cssText='position:absolute;pointer-events:none;object-fit:fill;margin:0;padding:0;border:0;';
                        const p=c.offsetParent,pr=p?p.getBoundingClientRect():{left:0,top:0};
                        Object.assign(image.style,{left:(r.left-pr.left+(p?p.scrollLeft:scrollX))+'px',top:(r.top-pr.top+(p?p.scrollTop:scrollY))+'px',width:r.width+'px',height:r.height+'px',zIndex:getComputedStyle(c).zIndex});
                        c.after(image);frames.push(image);
                      }catch(e){errors.push(String(e).slice(0,200));}
                    }return JSON.stringify({frames:frames.length,errors});
                  };
                })();
            """.trimIndent()
    fun install(web: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf("*"))
    }
    suspend fun prepare(web: WebView): List<String> {
        val result = web.browserJson("window.__kigttsCaptureCanvas?.(false)||'{}'")
        repeat(20) {
            val ready = web.browserJson("JSON.stringify({ready:Array.from(document.querySelectorAll('[data-kigtts-canvas-frame]')).every(i=>i.complete)})")
            if (ready.optBoolean("ready")) { web.awaitBrowserFrame(); return errors(result) }
            delay(25)
        }
        return errors(result) + "画布截图尚未完成解码，请重试截图"
    }
    suspend fun restore(web: WebView) { web.browserJson("window.__kigttsCaptureCanvas?.(true)||'{}'") }
    private fun errors(result: org.json.JSONObject): List<String> = result.optJSONArray("errors")?.let { values ->
        (0 until values.length()).map { "画布捕获：" + values.optString(it) }
    }.orEmpty()
}
