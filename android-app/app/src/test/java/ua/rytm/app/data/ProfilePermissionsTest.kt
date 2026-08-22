package ua.rytm.app.data

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilePermissionsTest {
    @Test fun ownerCanEdit() = assertTrue(canEditProfile("owner", null, emptyList(), emptyMap()))
    @Test fun explicitOwnerCanEdit() = assertTrue(canEditProfile("owner", "owner", emptyList(), emptyMap()))
    @Test fun legacyMemberDefaultsToEditor() = assertTrue(canEditProfile("member", "owner", listOf("member"), emptyMap()))
    @Test fun editorCanEdit() = assertTrue(canEditProfile("member", "owner", listOf("member"), mapOf("member" to "editor")))
    @Test fun viewerCannotEdit() = assertFalse(canEditProfile("member", "owner", listOf("member"), mapOf("member" to "viewer")))
    @Test fun removedMemberCannotEditEvenWithStaleRole() = assertFalse(canEditProfile("member", "owner", emptyList(), mapOf("member" to "editor")))
    @Test fun unknownRoleIsDenied() = assertFalse(canEditProfile("member", "owner", listOf("member"), mapOf("member" to "owner")))
}
