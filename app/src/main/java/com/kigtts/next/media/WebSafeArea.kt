package com.kigtts.next.media

/** Idempotent in-document padding plus an API for fixed-position plugin UI. No document rewriting. */
object WebSafeArea {
    fun script(top: Int, right: Int, bottom: Int, left: Int, physicalWidth: Int, physicalHeight: Int): String {
        require(listOf(top, right, bottom, left).all { it >= 0 } && physicalWidth > 0 && physicalHeight > 0)
        return """
            (function(){
              const root=document.documentElement,body=document.body;if(!root||!body)return;
              const ratio=window.innerWidth/$physicalWidth;
              const inset={top:$top*ratio,right:$right*ratio,bottom:$bottom*ratio,left:$left*ratio};
              root.style.setProperty('--kigtts-viewport-height',($physicalHeight*ratio)+'px');
              let style=document.getElementById('kigtts-card-safe-area');
              if(!style){
                const original=getComputedStyle(body);
                ['top','right','bottom','left'].forEach(side=>root.style.setProperty('--kigtts-original-padding-'+side,'calc('+original.getPropertyValue('padding-'+side)+' + '+original.getPropertyValue('margin-'+side)+')'));
                style=document.createElement('style');style.id='kigtts-card-safe-area';
                style.textContent='html{scroll-padding:var(--kigtts-safe-area-top) var(--kigtts-safe-area-right) var(--kigtts-safe-area-bottom) var(--kigtts-safe-area-left)}body{box-sizing:border-box!important;margin:0!important;min-height:var(--kigtts-viewport-height)!important;padding-top:calc(var(--kigtts-original-padding-top) + var(--kigtts-safe-area-top))!important;padding-right:calc(var(--kigtts-original-padding-right) + var(--kigtts-safe-area-right))!important;padding-bottom:calc(var(--kigtts-original-padding-bottom) + var(--kigtts-safe-area-bottom))!important;padding-left:calc(var(--kigtts-original-padding-left) + var(--kigtts-safe-area-left))!important}';
                (document.head||root).appendChild(style);
              }
              ['top','right','bottom','left'].forEach(side=>root.style.setProperty('--kigtts-safe-area-'+side,inset[side]+'px'));
              window.KigttsSafeArea=inset;
              window.dispatchEvent(new CustomEvent('kigtts-safe-area-change',{detail:inset}));
            })();
        """.trimIndent()
    }
}
