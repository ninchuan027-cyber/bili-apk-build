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
          var KEYS = ['广告屏蔽', '插件白名单', '广告拦截'];
          var timer = null;

          try {
            var origAttach = Element.prototype.attachShadow;
            Element.prototype.attachShadow = function(init){
              return origAttach.call(this, Object.assign({}, init || {}, {mode: 'open'}));
            };
          } catch(e){}

          function hasKey(t){ for (var i = 0; i < KEYS.length; i++) if (t.indexOf(KEYS[i]) > -1) return true; return false; }
          function pos(e){ return getComputedStyle(e).position; }
          function small(e){ return (e.textContent || '').length < 600; }
          function covers(e){
            var r = e.getBoundingClientRect();
            return r.width >= innerWidth * 0.9 && r.height >= innerHeight * 0.9;
          }
          function pickByGeometry(el){
            var c = el;
            while (c && c !== document.body && c !== document.documentElement) {
              var r = c.getBoundingClientRect();
              if (r.width >= innerWidth * 0.8 && r.height >= innerHeight * 0.2 && small(c)) return c;
              c = c.parentElement;
            }
            return null;
          }
          function pickByPosition(el){
            var c = el, fixedBox = null, absBox = null;
            while (c && c !== document.body && c !== document.documentElement && small(c)) {
              var p = pos(c);
              if (p === 'fixed') fixedBox = c; else if (p === 'absolute') absBox = c;
              c = c.parentElement;
            }
            return fixedBox || absBox;
          }
          function pickBox(el){ return pickByGeometry(el) || pickByPosition(el); }

          function killMasks(){
            var els = document.body.querySelectorAll('*');
            for (var i = 0; i < els.length; i++) {
              var e = els[i], p = pos(e);
              if ((p === 'fixed' || p === 'absolute') && (e.textContent || '').trim().length < 20 && covers(e)) {
                var m = /rgba?\(([^)]*)\)/.exec(getComputedStyle(e).backgroundColor || '');
                var a = 0;
                if (m) { var parts = m[1].split(','); a = parts.length > 3 ? parseFloat(parts[3]) : 1; }
                if (a > 0.05) e.remove();
              }
              else if (p === 'fixed' && covers(e) && e.children.length === 0 && (e.tagName === 'DIV' || e.tagName === 'SPAN') && !(e.textContent || '').trim()) {
                e.remove();
              }
            }
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
            document.body.style.setProperty('overflow', 'auto', 'important');
          }

          function killBoxes(){
            var imgs = document.body.querySelectorAll('img'), seen = [], did = false;
            for (var i = 0; i < imgs.length; i++) {
              var c = imgs[i], fx = null, ab = null;
              while (c && c !== document.body && c !== document.documentElement) {
                var pp = pos(c);
                if (pp === 'fixed') fx = c; else if (pp === 'absolute') ab = c;
                c = c.parentElement;
              }
              fx = fx || ab;
              if (!fx || seen.indexOf(fx) >= 0) continue;
              seen.push(fx);
              if ((fx.textContent || '').trim().length >= 30) continue;
              var r = fx.getBoundingClientRect();
              var boxLike = r.width >= innerWidth * 0.75 && r.height >= innerHeight * 0.15 && r.height <= innerHeight * 0.6;
              if (boxLike || covers(fx)) { fx.remove(); did = true; }
            }
            return did;
          }

          function collect(root, topHost, out){
            var els = root.querySelectorAll('*');
            for (var i = 0; i < els.length; i++) {
              var e = els[i], t = e.textContent;
              if (t && t.length < 300 && (hasKey(t) || t.trim() === '允许')) {
                var target = topHost || e;
                if (out.indexOf(target) < 0) out.push(target);
              }
              if (e.shadowRoot) collect(e.shadowRoot, topHost || e, out);
            }
          }
          function sweep(){
            timer = null;
            if (!document.body) return;
            var hits = [], did = false;
            collect(document.documentElement, null, hits);
            hits.forEach(function(el){
              if (!el.isConnected) return;
              var box = pickBox(el);
              if (box && box.isConnected) { box.remove(); did = true; }
            });
            if (killBoxes()) did = true;
            if (did) { killMasks(); setTimeout(killMasks, 300); }
          }
          function schedule(){ if (!timer) timer = setTimeout(sweep, 400); }

          function unlock(){
            [document.documentElement, document.body].forEach(function(e){
              if (!e) return;
              var cs = getComputedStyle(e);
              if (cs.overflowY === 'hidden' || cs.overflowY === 'clip') e.style.setProperty('overflow', 'auto', 'important');
              if (cs.touchAction === 'none') e.style.setProperty('touch-action', 'auto', 'important');
            });
          }
          setInterval(unlock, 500);
          var obs = null;
          function startObs(){
            if (obs || !document.documentElement) return;
            obs = new MutationObserver(schedule);
            obs.observe(document.documentElement, {childList: true, subtree: true});
          }
          startObs();
          document.addEventListener('DOMContentLoaded', function(){ startObs(); schedule(); });
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
        web.loadUrl(home)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
