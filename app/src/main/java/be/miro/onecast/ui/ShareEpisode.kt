package be.miro.onecast.ui

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import be.miro.onecast.R
import be.miro.onecast.data.Episode
import java.io.File

/**
 * Hands a downloaded episode's file to whatever app the user picks — a file manager, another
 * player, a cloud upload. There's no "reveal in folder" on Android: the file lives in this app's
 * private storage (see `download/DownloadStore`), which no other app — file managers included —
 * can browse into. A [FileProvider] read grant plus the system share sheet is the closest
 * equivalent scoped storage allows.
 */
fun shareDownloadedEpisode(context: Context, episode: Episode) {
    val path = episode.downloadPath ?: return
    val file = File(path)
    if (!file.exists()) return

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
        ?: if (episode.downloadHasVideo) "video/*" else "audio/*"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, episode.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, context.getString(R.string.downloads_share)),
    )
}
