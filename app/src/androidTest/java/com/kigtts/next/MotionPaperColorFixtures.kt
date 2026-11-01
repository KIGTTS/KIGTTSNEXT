package com.kigtts.next

/** Fixed display-space reference pixels, shared by on-device and desktop GLES checks. */
internal const val MOTION_PAPER_COLOR_CASES = """[
  {"name":"plain video identity","video":[37,121,189,255],"overlay":[0,0,0,0],"expected":[37,121,189,255]},
  {"name":"white paper","video":[37,121,189,255],"overlay":[255,255,255,255],"expected":[255,255,255,255]},
  {"name":"gray frame","video":[37,121,189,255],"overlay":[128,128,128,255],"expected":[128,128,128,255]},
  {"name":"dark paper","video":[37,121,189,255],"overlay":[36,40,42,255],"expected":[36,40,42,255]},
  {"name":"colored frame","video":[37,121,189,255],"overlay":[28,106,113,255],"expected":[28,106,113,255]},
  {"name":"Remi blue sticker","video":[37,121,189,255],"overlay":[150,167,231,255],"expected":[150,167,231,255]},
  {"name":"translucent white sticker","video":[37,121,189,255],"overlay":[255,255,255,128],"expected":[146,188,222,255]},
  {"name":"translucent color sticker","video":[37,121,189,255],"overlay":[210,80,130,96],"expected":[102,106,167,255]},
  {"name":"anti-aliased frame edge","video":[37,121,189,255],"overlay":[128,128,128,32],"expected":[48,122,181,255]},
  {"name":"filtered video","video":[37,121,189,255],"overlay":[0,0,0,0],"matrix":[0.8,0,0,0,12,0,1.1,0,0,-8,0,0,0.65,0,18,0,0,0,1,0],"expected":[42,125,141,255]},
  {"name":"filter leaves frame alone","video":[37,121,189,255],"overlay":[28,106,113,255],"matrix":[0.8,0,0,0,12,0,1.1,0,0,-8,0,0,0.65,0,18,0,0,0,1,0],"expected":[28,106,113,255]},
  {"name":"filter under translucent sticker","video":[37,121,189,255],"overlay":[210,80,130,96],"matrix":[0.8,0,0,0,12,0,1.1,0,0,-8,0,0,0.65,0,18,0,0,0,1,0],"expected":[105,108,137,255]},
  {"name":"blurred background","video":[37,121,189,255],"overlay":[0,0,0,0],"expected":[37,121,189,255],"blur":true},
  {"name":"empty photo placeholder","video":[37,121,189,255],"overlay":[0,0,0,0],"expected":[176,176,176,255],"gutter":true}
]"""
