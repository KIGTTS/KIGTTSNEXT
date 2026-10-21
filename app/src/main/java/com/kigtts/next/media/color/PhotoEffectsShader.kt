package com.kigtts.next.media.color

/** effectSample returns encoded RGB at normalized photograph coordinates, before color grading. */
internal object PhotoEffectsShader {
    const val GLSL = FILM_GRAIN_GLSL + """
        uniform vec4 uEffects;
        vec3 effectSample(vec2 uv);
        vec3 applyPhotoEffects(vec2 uv, vec3 source) {
            vec2 radial=uv-vec2(.5);
            float edge=dot(radial,radial)*2.;
            vec3 color=source;
            if(uEffects.z>.0001) {
                vec2 delta=radial*.018*edge*uEffects.z;
                color.r=effectSample(clamp(uv+delta,vec2(0.),vec2(1.))).r;
                color.b=effectSample(clamp(uv-delta,vec2(0.),vec2(1.))).b;
            }
            if(uEffects.w>.0001) {
                vec3 light=max(effectSample(clamp(uv+vec2(.008,0.),vec2(0.),vec2(1.)))-.65,vec3(0.));
                light+=max(effectSample(clamp(uv-vec2(.008,0.),vec2(0.),vec2(1.)))-.65,vec3(0.));
                light+=max(effectSample(clamp(uv+vec2(0.,.008),vec2(0.),vec2(1.)))-.65,vec3(0.));
                light+=max(effectSample(clamp(uv-vec2(0.,.008),vec2(0.),vec2(1.)))-.65,vec3(0.));
                color=1.-(1.-color)*(1.-light/1.4*.4*uEffects.w);
            }
            color*=1.-.72*uEffects.x*smoothstep(.15,.85,edge);
            if(uEffects.y>.0001) {
                vec2 cell=floor(uv*2048.);
                float n=filmGrain(cell);
                color+=n*.12*uEffects.y;
            }
            return clamp(color,0.,1.);
        }
    """
}
