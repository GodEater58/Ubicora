package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

/** 4.26: the plain rules behind the new order of the screens. */
class LayoutOrderTest {
    @Test fun descriptionShownOnlyWhenTheSearchFoundItThere() {
        val name = "Avvitatore 18 V"; val note = "Elettroutensile a batteria"
        assertFalse(ArticleRowText.showNote(name, note, ""))              // no search: short rows
        assertFalse(ArticleRowText.showNote(name, note, "avvitatore"))    // found in the name already
        assertTrue(ArticleRowText.showNote(name, note, "Batteria"))       // found only in the description
        assertTrue(ArticleRowText.showNote(name, note, "18  batteria"))   // one of more words is enough
        assertFalse(ArticleRowText.showNote(name, "", "batteria"))        // nothing to show
        assertTrue(ArticleRowText.showNote(name, note, "viti,batteria"))  // split like the search: commas too
    }
}
