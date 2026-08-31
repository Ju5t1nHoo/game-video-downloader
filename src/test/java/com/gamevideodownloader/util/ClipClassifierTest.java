package com.gamevideodownloader.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClipClassifierTest {

    @Test
    void freeClipIsNotPaid() {
        assertFalse(ClipClassifier.isPaid("https://qxtechdata.qiuxinkaifa.com/vslicing/202608/27/abc.mp4"));
    }

    @Test
    void paidClipIsPaid() {
        assertTrue(ClipClassifier
                .isPaid("https://qxtechdata.qiuxinkaifa.com/bepaid/202608/27/vslicing/202608/27/abc.mp4"));
    }

    @Test
    void nullPathIsNotPaid() {
        assertFalse(ClipClassifier.isPaid(null));
    }
}
