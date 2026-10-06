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

class BiliBridge(private val act: Activity) {
    @android.webkit.JavascriptInterface
    fun log(m: String) { act.runOnUiThread { android.widget.Toast.makeText(act, m, android.widget.Toast.LENGTH_LONG).show() } }
}

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
        "e.qq.com", "csjplatform.com", "pangolin-sdk-toutiao.com"
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
          document.addEventListener('DOMContentLoaded', function(){
            var s = document.createElement('style'); s.textContent = css;
            document.documentElement.appendChild(s);
          });
        })();
    """.trimIndent()

    private val killerJs = """
        (function(){
          var KEYS = ['广告�s蔽', '插件白名单', '广告拦截'];
          var timer = null, removed = 0, closedCount = 0, log = [];
          function say(m){ try { BiliBridge.log(m); } catch(e){} }

          try {
            var origAttach = Element.prototype.attachShadow;
            Element.prototype.attachShadow = function(init){
              if (init && init.mode === 'closed') closedCount++;
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
          function desc(e){
            if (!e || !e.tagName) return '?';
            var c = (typeof e.className === 'string' ? e.className : '').trim().split(/\s+/).slice(0, 2).join('.');
            return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (c ? '.' + c : '') + '[' + pos(e).charAt(0) + ']';
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
            var c = el, chain = [];
            while (c && c !== document.body && c !== document.documentElement && small(c)) { chain.push(c); c = c.parentElement; }
            if (!chain.length) return null;
            var fixedBox = null, absBox = null;
            chain.forEach(function(x){
              var p = pos(x);
              if (p === 'fixed') fixedBox = x; else if (p === 'absolute') absBox = x;
            });
            if (fixedBox) return fixedBox;
            if (absBox) return absBox;
            var top = chain[chain.length - 1];
            if (top.parentElement === document.body) return top;
            return chain[Math.min(3, chain.length - 1)];
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
                if (a > 0.05) { log.push('遮罩:' + desc(e)); e.remove(); removed++; }
              }
            }
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
            document.body.style.setProperty('overflow', 'auto', 'important');
          }

          function scan(root, topHost, st){
            var els = root.querySelectorAll('*');
            for (var i = 0; i < els.length; i++) {
              var e = els[i], t = e.textContent;
              if (t && t.length < 300 && (hasKey(t) || t.trim() === '允许')) {
                if (topHost) { if (st.shadow.indexOf(topHost) < 0) st.shadow.push(topHost); }
                else st.hits.push(e);
              }
              if (e.shadowRoot) scan(e.shadowRoot, topHost || e, st);
              if (e.tagName === 'IFRAME') {
                st.iframes++;
                try {
                  var d = e.contentDocument;
                  if (d && d.body && hasKey(d.body.textContent || '')) { e.remove(); removed++; }
                } catch(x){}
              }
            }
          }
          function stats(){
            var st = {hits: [], shadow: [], iframes: 0};
            if (document.documentElement) scan(document.documentElement, null, st);
            return st;
          }
          function sweep(){
            timer = null;
            if (!document.body) return;
            var st = stats(), did = false;
            st.hits.concat(st.shadow).forEach(function(el){
              if (!el.isConnected) return;
              var box = pickBox(el);
              if (box && box.isConnected) {
                log.push(desc(el) + '>' + desc(box));
                box.remove(); removed++; did = true;
              }
            });
            if (did) { killMasks(); setTimeout(killMasks, 300); }
          }
          function schedule(){ if (!timer) timer = setTimeout(sweep, 200); }
          document.addEventListener('DOMContentLoaded', function(){
            new MutationObserver(schedule).observe(document.documentElement, {childList: true, subtree: true, characterData: true});
            schedule();
          });
          setInterval(sweep, 1000);

          if (window === window.top) setTimeout(function(){
            var st = stats();
            say('v8诊断 命中=' + st.hits.length + ' closed=' + closedCount + ' iframe=' + st.iframes + ' 已删=' + removed);
            setTimeout(function(){ say('删除记录=' + (log.join(' | ') || '无')); }, 3800);
          }, 5000);
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

        WebView.setWebContentsDebuggingEnabled(true)
        android.widget.Toast.makeText(this, "Bili v8（带诊断）", android.widget.Toast.LENGTH_LONG).show()
        web = WebView(this)
        web.addJavascriptInterface(BiliBridge(this), "BiliBridge")
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
