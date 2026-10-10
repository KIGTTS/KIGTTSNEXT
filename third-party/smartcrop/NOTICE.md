# smartcrop.js Kotlin port

Original: https://github.com/jwagner/smartcrop.js

Pinned revision: `0e207ed910625f91b07d35865c71a9621a63ebdb`.

Copyright (C) 2018 Jonas Wagner (source header); Copyright (c) 2016 Jonas Wagner (repository license).
Distributed under the MIT license in `LICENSE`, also bundled in the APK at `assets/licenses/smartcrop-MIT.txt`.

The Kotlin port preserves edge, skin-color and saturation heuristics, boost rectangles, block sampling, candidate traversal and default importance scoring. Native additions are cancellation, input validation, small-image handling, premultiplied bilinear prescaling and a configurable focal point. Browser Canvas interpolation is not claimed to be pixel-identical. The development-only Node generator produces independent upstream reference fixtures; JavaScript is not used by the app.
