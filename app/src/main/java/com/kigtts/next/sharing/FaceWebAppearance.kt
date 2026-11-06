package com.kigtts.next.sharing

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Browser-only resources stay behind the revocable share URL, outside peer photo lists. */
internal class FaceWebAppearance(val json: JSONObject, private val files: Map<String, File>) {
    fun file(name: String) = files[name]?.takeIf { it.isFile }
    companion object {
        fun prepare(context: Context, directory: File): FaceWebAppearance {
            val files = mutableListOf<File>()
            val json = shareWebAppearance(context, directory.apply { mkdirs() }, files)
            return FaceWebAppearance(json, files.associateBy { it.name })
        }
    }
}

internal const val faceAppearanceStyles = """
:root{--share-font:system-ui;--profile-font-scale:1;--weight-400:400;--weight-500:500;--weight-600:600;--weight-700:700}
html{-webkit-tap-highlight-color:transparent}body,button,input,a{font-family:var(--share-font),system-ui,-apple-system,'Noto Sans SC',sans-serif}body{font-weight:var(--weight-400)}button,input,a{appearance:none;-webkit-tap-highlight-color:transparent}a,a:visited{color:inherit}button:focus-visible,a:focus-visible{outline:3px solid var(--md-sys-color-primary);outline-offset:3px}
.share-backdrop{position:fixed;inset:0;z-index:-1;pointer-events:none;background-size:cover;background-position:center;opacity:0;transition:opacity .25s}.share-backdrop.ready{opacity:1}.share-backdrop:after{content:'';position:absolute;inset:0;background:var(--md-sys-color-surface);opacity:var(--background-dim,.48)}body{isolation:isolate}
.profile h1{font-size:calc(24px * var(--profile-font-scale));line-height:32px;font-weight:var(--weight-400)}.bio{font-size:calc(16px * var(--profile-font-scale));line-height:24px}.profile-tags>span{font-size:calc(14px * var(--profile-font-scale));line-height:20px}
.profile-tile{min-height:0;gap:0;padding:16px;border-radius:var(--tile-corner,var(--kigtts-corner))}.profile-tile strong{font-size:calc(14px * var(--profile-font-scale));line-height:20px;font-weight:var(--weight-500)}.profile-tile span,.profile-tile small{font-size:calc(14px * var(--profile-font-scale));line-height:20px}.profile-tile.tile-compact{padding:8px 12px;gap:2px}.tile-compact strong{white-space:nowrap}.profile-tile.tile-compact span,.profile-tile.tile-compact small{font-size:calc(12px * var(--profile-font-scale));line-height:16px}.tile-compact:has(>svg){grid-template-rows:20px 16px}.profile-tile.tile-icon-only{display:flex;padding:8px;gap:0}.profile-tile.tile-text-title-only{padding:8px 12px}.profile-tile.tile-text-title-only strong{font-size:calc(22px * var(--profile-font-scale));font-weight:var(--weight-700);line-height:28px}.profile-tile.tile-title{padding:4px}.profile-tile.tile-title strong{font-size:calc(17px * var(--profile-font-scale));line-height:24px;font-weight:var(--weight-600)}.profile-tile.tile-image{padding:0}
.face-pending{position:relative;background:var(--md-sys-color-surface-container-high)}.face-pending:after,.face-font-pending .profile-tile:not(.tile-image):after{content:'';position:absolute;inset:0;background:var(--md-sys-color-surface-container-low);border-radius:inherit;pointer-events:none;animation:face-breathe 1.5s ease-in-out infinite}.face-font-pending .profile-tile:not(.tile-image):after{inset:40% 16px}.face-font-pending .profile-tile>:not(img){opacity:0}.profile-tile{position:relative}.face-image-loading{opacity:0!important}.profile img,.profile image,.photo img{transition:opacity .24s;animation:none}.face-enter{transition:opacity .24s,transform .24s}.face-enter.face-reveal-pending{opacity:0;transform:translateY(6px)}.face-enter.face-visible{opacity:1;transform:none}.face-font-pending .profile-tile{transition:none}.profile-tile>:not(img){transition:opacity .2s}.contact-dialog[open],.tile-image-viewer[open]{animation:face-dialog .18s both}.contact-dialog::backdrop,.tile-image-viewer::backdrop{animation:face-dialog .18s both}
@keyframes face-breathe{0%,100%{opacity:.3}50%{opacity:.75}}@keyframes face-dialog{from{opacity:0}to{opacity:1}}
@media(prefers-reduced-motion:reduce){.face-enter,.share-backdrop,.profile img,.photo img{transition:none}.face-enter{transform:none}.face-pending:after,.face-font-pending .profile-tile:after{animation:none}}
"""

internal fun faceAppearanceScript(appearance: JSONObject, path: String): String {
    val safe = appearance.toString().replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    return """
(()=>{const a=$safe,r=document.documentElement,base=${JSONObject.quote(path + "appearance/")};
const n=(v,min,max,f)=>Number.isFinite(v)?Math.max(min,Math.min(max,v)):f;
r.style.setProperty('--profile-font-scale',n(a.fontScale,.65,1.6,1));r.style.setProperty('--tile-corner',n(a.tileCorner,4,24,24)+'px');r.style.setProperty('--background-dim',n(a.backgroundDim,0,1,.48));
for(const w of [400,500,600,700])r.style.setProperty('--weight-'+w,n(a.weights?.[w],1,1000,w));
if(a.background){const b=document.createElement('div');b.className='share-backdrop';b.setAttribute('aria-hidden','true');document.body.prepend(b);const i=new Image();i.onload=()=>{b.style.backgroundImage='url("'+base+encodeURIComponent(a.background)+'")';b.classList.add('ready')};i.src=base+encodeURIComponent(a.background);}
if(a.font&&window.FontFace){r.classList.add('face-font-pending');const f=new FontFace('KigttsProfile','url("'+base+encodeURIComponent(a.font)+'")',{weight:Number.isFinite(a.weightMin)&&Number.isFinite(a.weightMax)?a.weightMin+' '+a.weightMax:'100 900'});const timer=setTimeout(()=>r.classList.remove('face-font-pending'),3000);f.load().then(font=>{document.fonts.add(font);r.style.setProperty('--share-font','KigttsProfile')}).catch(()=>{}).finally(()=>{clearTimeout(timer);r.classList.remove('face-font-pending')});}
document.querySelectorAll('.profile img,.photo img').forEach(i=>{const p=i.parentElement,done=()=>{p.classList.remove('face-pending');i.classList.remove('face-image-loading')};p.classList.add('face-pending');i.classList.add('face-image-loading');i.addEventListener('load',done,{once:true});i.addEventListener('error',done,{once:true});if(i.complete&&i.naturalWidth)done();});
const entries=[...document.querySelectorAll('.profile-cover,.avatar,.profile h1,.bio,.profile-tags,.profile-tile,.contact-link,article')];
if(window.IntersectionObserver){let count=0;const o=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.style.transitionDelay=Math.min(count++%7*35,210)+'ms';requestAnimationFrame(()=>{e.target.classList.add('face-visible');e.target.classList.remove('face-reveal-pending')});o.unobserve(e.target)}}),{rootMargin:'24px'});entries.forEach(e=>{e.classList.add('face-enter','face-reveal-pending');o.observe(e)});const restore=()=>entries.filter(e=>!e.classList.contains('face-visible')).forEach(e=>o.observe(e));const hide=e=>{o.disconnect();if(!e.persisted){window.removeEventListener('pageshow',restore);window.removeEventListener('pagehide',hide)}};window.addEventListener('pageshow',restore);window.addEventListener('pagehide',hide);}
})();
"""
}
