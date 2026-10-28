package com.mouse.bili

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.URL

class MainActivity : Activity() {
    private val home = "https://www.bilinovel.com/"
    private val chromeUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val adHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "adnxs.com", "taboola.com",
        "outbrain.com", "popads.net", "propellerads.com", "exoclick.com",
        "hm.baidu.com", "cpro.baidu.com", "pos.baidu.com", "union.baidu.com",
        "cnzz.com", "umeng.com", "tanx.com", "alimama.com", "gdt.qq.com",
        "e.qq.com", "csjplatform.com", "pangolin-sdk-toutiao.com",
        "fundingchoicesmessages.google.com", "fundingchoices.google.com", "adtrafficquality.google"
    )

    private val baseJs = """
        (function(){
          try {
            window.adsbygoogle = window.adsbygoogle || {loaded:true, push:function(){}};
            window.google_ad_client = window.google_ad_client || 'ca-pub-0';
            window.canRunAds = true; window._hmt = window._hmt || []; window.dataLayer = window.dataLayer || [];
            window.ga = window.ga || function(){};
            window.gtag = window.gtag || function(){};
          } catch(e){}
          var css = 'ins.adsbygoogle,iframe[src*="doubleclick"],iframe[src*="googlesyndication"],' +
            '[id^="google_ads"],[id^="div-gpt-ad"],[id*="BAIDU_"],[id^="cpro"]' +
            '{opacity:0!important;pointer-events:none!important}';
          css += '.fc-ab-root,.fc-dialog-container,.fc-dialog-overlay,.fc-consent-root,.fc-dialog{display:none!important}';
          try {
            var sheet = new CSSStyleSheet();
            sheet.replaceSync(css);
            document.adoptedStyleSheets = (document.adoptedStyleSheets || []).concat([sheet]);
          } catch(e) {
            document.addEventListener('DOMContentLoaded', function(){
              var s = document.createElement('style'); s.textContent = css;
              document.documentElement.appendChild(s);
            });
          }
        })();
    """.trimIndent()

    private val killerJs = """
        (function(){
          function unlock(){
            [document.documentElement, document.body].forEach(function(e){
              if (!e) return;
              var cs = getComputedStyle(e);
              if (cs.overflowY === 'hidden' || cs.overflowY === 'clip') e.style.setProperty('overflow', 'auto', 'important');
              if (cs.touchAction === 'none') e.style.setProperty('touch-action', 'auto', 'important');
            });
          }
          function sweep(){
            var els = document.querySelectorAll('.fc-ab-root, .fc-dialog-container, .fc-dialog-overlay, .fc-consent-root');
            for (var i = 0; i < els.length; i++) els[i].remove();
            unlock();
          }
          setInterval(sweep, 1000);
        })();
    """.trimIndent()

    private val guestJs: String get() = baseJs + "\n" + killerJs

    private lateinit var web: WebView

    private fun isAd(url: String): Boolean = try {
        val h = URL(url).host
        adHosts.any { h == it || h.endsWith(".$it") }
    } catch (e: Exception) { false }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        web = WebView(this)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            userAgentString = chromeUa
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, guestJs, setOf("*"))
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                if (!isAd(u)) return null
                val mime = if (u.substringBefore('?').endsWith(".js")) "application/javascript" else "text/plain"
                return WebResourceResponse(
                    mime, "utf-8", 200, "OK",
                    mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = isAd(r.url.toString())
            override fun onPageStarted(v: WebView, url: String, f: android.graphics.Bitmap?) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) v.evaluateJavascript(guestJs, null)
            }
        }

        setContentView(FrameLayout(this).apply { addView(web, FrameLayout.LayoutParams(-1, -1)) })
        val lastUrl = getSharedPreferences("bili", MODE_PRIVATE).getString("last_url", null)
        web.loadUrl(if (lastUrl != null && isSiteUrl(lastUrl)) lastUrl else home)
    }

    private fun isSiteUrl(u: String): Boolean = try {
        val h = URL(u).host
        h.endsWith("bilinovel.com") || h.endsWith("linovelib.com")
    } catch (e: Exception) { false }

    override fun onPause() {
        super.onPause()
        val u = web.url
        if (u != null && isSiteUrl(u) && !u.contains("search")) {
            getSharedPreferences("bili", MODE_PRIVATE).edit().putString("last_url", u).apply()
        }
    }

    private fun isHomeUrl(u: String?): Boolean =
        u != null && u.trimEnd('/') == home.trimEnd('/')

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            web.canGoBack() -> web.goBack()
            !isHomeUrl(web.url) -> web.loadUrl(home)
            else -> super.onBackPressed()
        }
    }
}
