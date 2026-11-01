package com.kigtts.next

import com.kigtts.next.ui.cards.*
import org.junit.Assert.*
import org.junit.Test

class CardImageResolutionTest {
    @Test fun portraitPhotosUseTheirHeightAndViewerZoomRequestsSharperPixels() {
        assertEquals(2048, cardPhotoDecodeSize(850f, 1800f, 1.0, 1f, 4096))
        assertEquals(4096, cardPhotoDecodeSize(850f, 1800f, 1.0, 2f, 4096))
        assertEquals(3072, cardPhotoDecodeSize(850f, 1800f, 1.5, 1f, 4096))
    }
    @Test fun nearbySizesReuseTheSameLevelAndThumbnailsKeepTheirMemoryLimit() {
        assertEquals(cardImageDecodeSize(1300f, 4096), cardImageDecodeSize(1490f, 4096))
        assertEquals(512, cardPhotoDecodeSize(800f, 1600f, 8.0, 4f, 512))
        assertEquals(4096, cardImageDecodeSize(10000f, 4096))
        assertEquals(128, cardImageDecodeSize(Float.NaN, 4096))
    }
}
