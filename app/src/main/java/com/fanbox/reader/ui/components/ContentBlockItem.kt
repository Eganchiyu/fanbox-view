package com.fanbox.reader.ui.components

import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.fanbox.reader.R
import com.fanbox.reader.data.model.Block

@Composable
fun ContentBlockItem(block: Block, images: List<Uri>, openImage: (List<Uri>, Int) -> Unit) {
    when (block) {
        is Block.Image -> AsyncImage(
            model = block.file.uri,
            contentDescription = block.file.name,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .clickable { openImage(images, images.indexOf(block.file.uri).coerceAtLeast(0)) },
            contentScale = ContentScale.FillWidth,
            onError = { Log.e("FanboxReader", "加载图片失败: ${block.file.name} / ${block.file.uri}", it.result.throwable) }
        )
        is Block.Text -> {
            if (block.isHtml) {
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            textSize = 16f
                            text = Html.fromHtml(block.content, Html.FROM_HTML_MODE_COMPACT, Html.ImageGetter { null }, null)
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            } else {
                Text(block.content, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is Block.Video -> InlineVideoPlayer(block.file.uri)
        is Block.Unsupported -> Text(stringResource(R.string.unsupported_type, block.name ?: ""), Modifier.padding(20.dp))
    }
}
