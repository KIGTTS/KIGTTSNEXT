package com.kigtts.next.cards

internal object MotionPaperShaders {
    const val VERTEX = """attribute vec4 aPosition; varying vec2 vUv; void main(){gl_Position=aPosition;vUv=aPosition.xy*.5+.5;}"""
    const val GAUSSIAN = """precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;uniform vec2 uStep;
      void main(){vec4 color=vec4(0.);float total=0.;for(int i=-12;i<=12;i++){float x=float(i);float weight=exp(-x*x/32.);
      color+=texture2D(uTexture,clamp(vUv+uStep*x,vec2(.001),vec2(.999)))*weight;total+=weight;}gl_FragColor=color/total;}"""
    const val PAPER = """precision highp float;varying vec2 vUv;
      uniform sampler2D uVideo;uniform sampler2D uBlur;uniform sampler2D uOverlay;
      uniform vec2 uOutputSize;uniform vec4 uPhoto;uniform vec4 uForeground;uniform vec4 uBackdrop;
      uniform vec4 uRow0;uniform vec4 uRow1;uniform vec4 uRow2;uniform vec3 uBias;uniform float uBlurred;uniform float uRadius;
      vec3 filtered(vec4 c){vec4 encoded=vec4(pow(clamp(c.rgb,0.,1.),vec3(1./2.2)),c.a);
        return pow(clamp(vec3(dot(uRow0,encoded),dot(uRow1,encoded),dot(uRow2,encoded))+uBias,0.,1.),vec3(2.2));}
      vec2 uvFor(vec2 point,vec4 rect){vec2 uv=(point-rect.xy)/rect.zw;return vec2(uv.x,1.-uv.y);}
      bool inside(vec2 uv){return uv.x>=0.&&uv.x<=1.&&uv.y>=0.&&uv.y<=1.;}
      void main(){vec2 point=vec2(vUv.x,1.-vUv.y)*uOutputSize;
        vec2 q=abs(point-(uPhoto.xy+uPhoto.zw*.5))-(uPhoto.zw*.5-vec2(uRadius));
        float edge=length(max(q,vec2(0.)))+min(max(q.x,q.y),0.)-uRadius;
        vec3 base=vec3(pow(.690196,2.2));
        if(edge<=0.){if(uBlurred>.5)base=filtered(texture2D(uBlur,clamp(uvFor(point,uBackdrop),vec2(.001),vec2(.999))));
          vec2 foreground=uvFor(point,uForeground);if(inside(foreground))base=filtered(texture2D(uVideo,foreground));}
        vec4 over=texture2D(uOverlay,vec2(vUv.x,1.-vUv.y));
        vec3 overlayLinear=pow(clamp(over.rgb/max(over.a,.0001),0.,1.),vec3(2.2))*over.a;
        gl_FragColor=vec4(overlayLinear+base*(1.-over.a),1.);
      }"""
}
