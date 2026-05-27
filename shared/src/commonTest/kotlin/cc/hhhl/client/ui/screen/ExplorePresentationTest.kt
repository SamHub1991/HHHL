package cc.hhhl.client.ui.screen

import cc.hhhl.client.model.FlashListKind
import cc.hhhl.client.model.GalleryListKind
import cc.hhhl.client.model.PageListKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ExplorePresentationTest {
    @Test
    fun galleryKeepsPublicDiscoveryKindsVisible() {
        assertEquals(
            listOf(
                GalleryListKind.Featured,
                GalleryListKind.Popular,
                GalleryListKind.Recent,
            ),
            galleryPrimaryKinds(),
        )
        assertEquals(
            listOf(
                GalleryListKind.Mine,
                GalleryListKind.Liked,
            ),
            galleryOverflowKinds(),
        )
    }

    @Test
    fun flashKeepsPersonalKindsInOverflow() {
        assertEquals(
            listOf(FlashListKind.Featured),
            flashPrimaryKinds(),
        )
        assertEquals(
            listOf(
                FlashListKind.Mine,
                FlashListKind.Liked,
            ),
            flashOverflowKinds(),
        )
    }

    @Test
    fun flashWebPathTargetsPlayRoute() {
        assertEquals("/play/flash-1", flashWebPath("flash-1"))
        assertEquals("/play/flash-2", flashWebPath(" flash-2 "))
        assertEquals("/play", flashWebPath(" "))
    }

    @Test
    fun pageKeepsPersonalKindsInOverflow() {
        assertEquals(
            listOf(
                PageListKind.Featured,
            ),
            pagePrimaryKinds(),
        )
        assertEquals(
            listOf(PageListKind.Mine),
            pageOverflowKinds(),
        )
    }
}
