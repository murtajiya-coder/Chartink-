package com.stocktools.dual

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri

enum class Site(val label: String, val url: String, @DrawableRes val icon: Int) {
    CHARTINK("Chartink", "https://chartink.com/dashboard/488230", R.drawable.ic_chartink),
    SCREENER("Screener.in", "https://www.screener.in/", R.drawable.ic_screener),
}

/**
 * Owns one WebView for one website. Each site gets its own WebView instance that lives for the
 * whole Activity lifetime, so switching tabs never reloads a page. Cookies are stored per domain
 * by Android's CookieManager, so chartink.com and screener.in keep fully separate sessions.
 */
@SuppressLint("SetJavaScriptEnabled")
class SiteController(private val activity: Activity, val site: Site) {

    val webView: WebView = WebView(activity)

    var progress by mutableIntStateOf(0); private set
    var isLoading by mutableStateOf(true); private set
    var hasError by mutableStateOf(false); private set
    var canGoBack by mutableStateOf(false); private set

    init {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.apply {
            // Secure Android Autofill (password manager / Google Autofill) for login forms.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Respect the site's own responsive <meta viewport>; no desktop-mode override.
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                // Keep the sites' own HTTPS security intact.
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    hasError = false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    canGoBack = view?.canGoBack() == true
                    CookieManager.getInstance().flush()
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    canGoBack = view?.canGoBack() == true
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true && error?.errorCode != ERROR_UNKNOWN) {
                        hasError = true
                        isLoading = false
                    }
                }

                // Never bypass certificate problems.
                override fun onReceivedSslError(
                    view: WebView?, handler: SslErrorHandler?, error: SslError?
                ) {
                    handler?.cancel()
                    hasError = true
                    isLoading = false
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    return when (uri.scheme?.lowercase()) {
                        "https", "about", "blob", "data", "javascript" -> false
                        "http" -> {
                            view?.loadUrl(uri.buildUpon().scheme("https").build().toString())
                            true
                        }
                        else -> { openExternally(uri); true } // mailto:, tel:, intent: ...
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }

            // Exports (CSV / Excel) from Screener and Chartink go through the system DownloadManager.
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimeType)
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                        addRequestHeader("User-Agent", userAgent)
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType)
                        )
                    }
                    (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                        .enqueue(request)
                    Toast.makeText(activity, "Downloading…", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    openExternally(Uri.parse(url))
                }
            }

            loadUrl(site.url)
        }
    }

    fun retry() {
        hasError = false
        isLoading = true
        val current = webView.url
        if (current.isNullOrBlank() || current.startsWith("about:") || current.startsWith("data:")) {
            webView.loadUrl(site.url)
        } else {
            webView.reload()
        }
    }

    fun destroy() {
        webView.stopLoading()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun openExternally(uri: Uri) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
