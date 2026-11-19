package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.21: the article being created comes back as it was after the screen lock or a closed app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NewItemDraftTest {
    @Test fun aDraftComesBackExactlyAsItWasLeft() {
        val context = RuntimeEnvironment.getApplication()
        val draft = NewItemDraft(7, step = 3, name = "Sensore induttivo M12", category = "Elettrico", qty = "4", min = "2",
            unit = "pz", note = "PNP, NA", cost = "18,5", supplier = "Rossi", lead = "5", photoUri = null, copiedFrom = 31L,
            place = NewItemPlace(12L, 2, 3), moreDetails = true)
        NewItemDrafts.save(context, draft)
        assertEquals(draft, NewItemDrafts.load(context, 7))
        assertNull(NewItemDrafts.load(context, 8))                 // another site has its own
        NewItemDrafts.clear(context, 7)
        assertNull(NewItemDrafts.load(context, 7))
    }

    @Test fun theMapChoiceAndAnEmptyPlaceSurviveToo() {
        val onMap = NewItemDraft(3, place = NewItemPlace(onMap = true))
        assertEquals(onMap, NewItemDraft.fromJson(onMap.toJson()))
        val none = NewItemDraft(3, step = 2, name = "Molla")
        assertEquals(none, NewItemDraft.fromJson(none.toJson()))
    }
}
