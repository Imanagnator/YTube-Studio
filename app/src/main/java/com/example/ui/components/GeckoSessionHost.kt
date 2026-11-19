package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.SignalCrimson
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun GeckoSessionHost(
    session: GeckoSession,
    isEngineReady: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isEngineReady || !session.isOpen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SignalCrimson)
        }
        return
    }

    val context = LocalContext.current
    val geckoView = remember(context) {
        GeckoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(geckoView, session, isEngineReady) {
        if (isEngineReady && session.isOpen && geckoView.session !== session) {
            geckoView.setSession(session)
        }
        onDispose {
            if (geckoView.session === session) {
                geckoView.releaseSession()
            }
        }
    }

    AndroidView(
        factory = { geckoView },
        modifier = modifier
    )
}
