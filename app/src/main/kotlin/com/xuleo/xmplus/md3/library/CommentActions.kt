package com.xuleo.xmplus.md3.library

import com.xuleo.xmplus.md3.core.account.AccountRepository
import com.xuleo.xmplus.md3.core.model.Credentials
import com.xuleo.xmplus.md3.core.model.XmlyException
import com.xuleo.xmplus.md3.core.protocol.TrackComment
import com.xuleo.xmplus.md3.core.protocol.TrackCommentPage
import com.xuleo.xmplus.md3.core.protocol.XmlyComments

/** Track comments for the player. Reading works without an account; every write uses the primary account. */
class CommentActions(private val accounts: AccountRepository, private val comments: XmlyComments) {
    /** The primary account's uid, or null when nobody is signed in. Used to tell the user's own comments apart. */
    suspend fun uid(): String? = accounts.primaryCredentials()?.uid

    suspend fun signedIn(): Boolean = accounts.primaryCredentials() != null

    suspend fun load(trackId: String, page: Int): TrackCommentPage =
        comments.load(trackId, page, accounts.primaryCredentials())

    suspend fun post(trackId: String, content: String) = comments.create(credentials(), trackId, content)

    suspend fun setLiked(trackId: String, comment: TrackComment, liked: Boolean) =
        comments.setLiked(credentials(), trackId, comment, liked)

    suspend fun delete(trackId: String, comment: TrackComment) = comments.delete(credentials(), trackId, comment)

    private suspend fun credentials(): Credentials =
        accounts.primaryCredentials() ?: throw XmlyException("login_required", definitive = true)
}