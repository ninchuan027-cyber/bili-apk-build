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

    // 页面脚本执行前注入。
    // 关键：不再用 display:none 隐藏广告容器——"容器高度为0/被隐藏"正是最常见的检测方式。
    // 改成 opacity:0（尺寸、可见性、display 都保持正常），视觉上同样看不到。
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


    // 站点自己的"检测到广告屏蔽"弹窗：按文字找到它所在的固定定位层，连同遮罩一起移除，并解除滚动锁定。
    // （注意：Kotlin 原始字符串里不能出现美元符号，下面的 JS 里没有用）
    private val killerJs = """
        (function(){
          var KEYS = ['广告屏蔽', '插件白名单', '广告拦截'];
          var timer = null, removed = 0;
          function say(m){ try { BiliBridge.log(m); } catch(y){} }
          function hasKey(t){ for (var i = 0; i < KEYS.length; i++) if (t.indexOf(KEYS[i]) > -1) return true; return false; }
          function pos(e){ return getComputedStyle(e).position; }
          function small(e){ return (e.textContent || '').length < 600; }
          function covers(e){
            var r = e.getBoundingClientRect();
            return r.width >= innerWidth * 0.9 && r.height >= innerHeight * 0.9;
          }
          function pickBox(el){
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
          function stats(){
            var st = {hits: [], shadow: [], iframes: 0, iframeHit: 0};
            if (!document.body) return st;
            var els = document.body.querySelectorAll('*');
            for (var i = 0; i < els.length; i++) {
              var e = els[i], t = e.textContent;
              if (t && t.length < 300 && hasKey(t)) st.hits.push(e);          // 按元素文字匹配，文字被拆成多段也能命中
              if (e.shadowRoot && hasKey(e.shadowRoot.textContent || '')) st.shadow.push(e);
              if (e.tagName === 'IFRAME') {
                st.iframes++;
                try {
                  var d = e.contentDocument;
                  if (d && d.body && hasKey(d.body.textContent || '')) { st.iframeHit++; e.remove(); removed++; }
                } catch(x){}
              }
            }
            return st;
          }
          function sweep(){
            timer = null;
            if (!document.body) return;
            var st = stats();
            st.hits.concat(st.shadow).forEach(function(el){
              if (!el.isConnected) return;
              var box = pickBox(el);
              if (box && box.isConnected) { box.remove(); removed++; }
            });
            if (removed > 0) {
              Array.prototype.forEach.call(document.body.children, function(e){
                var p = pos(e);
                if ((p === 'fixed' || p === 'absolute') && covers(e) && (e.textContent || '').trim().length < 20) e.remove();
              });
              document.documentElement.style.setProperty('overflow', 'auto', 'important');
              document.body.style.setProperty('overflow', 'auto', 'important');
            }
          }
          function schedule(){ if (!timer) timer = setTimeout(sweep, 200); }
          document.addEventListener('DOMContentLoaded', function(){
            new MutationObserver(schedule).observe(document.documentElement, {childList: true, subtree: true, characterData: true});
            schedule();
          });
          setInterval(sweep, 1000);
          // 诊断：5 秒后在屏幕下方提示一次，方便判断弹窗到底藏在哪
          if (window === window.top) setTimeout(function(){
            var st = stats();
            var inHtml = document.documentElement.outerHTML.indexOf('广告屏蔽') > -1;
            say('v5诊断 源码含关键词=' + (inHtml ? '是' : '否') + ' 元素命中=' + st.hits.length +
                ' shadow=' + st.shadow.length + ' iframe=' + st.iframes + ' 已删=' + removed);
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

        WebView.setWebContentsDebuggingEnabled(true) // 调试用：电脑 Chrome 打开 chrome://inspect 可检查本应用网页
        android.widget.Toast.makeText(this, "Bili v5（带诊断）", android.widget.Toast.LENGTH_LONG).show()
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
                // 返回 200 + 允许跨域 + 空内容，fetch/XHR/script/img 探测都"成功"，不触发 onerror
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

        // 全屏只放网页，没有地址栏/按钮栏；返回键 = 网页后退
        setContentView(FrameLayout(this).apply { addView(web, FrameLayout.LayoutParams(-1, -1)) })
        web.loadUrl(home)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
