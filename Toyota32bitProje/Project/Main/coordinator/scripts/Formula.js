// myFormula.js   –  Çalışan sürümün küçük rötüşlü hâli (ES5)

/* ---------- yardımcılar (ES5, endsWith yok) --------------------------- */
function endsWith(str, suf) {
    return str.indexOf(suf, str.length - suf.length) !== -1;
}
/* context’te anahtarı suf ile bitenlerin ortalamasını döner; veri yoksa hata */
function avgBySuffix(ctx, suf) {
    var it = ctx.keySet().iterator();
    var sum = 0.0, cnt = 0;
    while (it.hasNext()) {
        var k = String(it.next());
        if (endsWith(k, suf)) {
            var v = ctx.get(k);
            if (v != null) { sum += v; cnt++; }
        }
    }
    if (cnt === 0) { throw "No data for suffix " + suf; }
    return sum / cnt;
}
/* ---------- ana fonksiyon --------------------------------------------- */
function compute(context) {

    var calcName = String(context.get("calcName"));   // "USDTRY", "EURUSD" …

    /* 1) Her hesaplama USDTRY ortalamasını ister */
    var usdBid = avgBySuffix(context, "UsdtryBid");
    var usdAsk = avgBySuffix(context, "UsdtryAsk");

    /* 2) Doğrudan USDTRY ise hemen dön */
    if (calcName === "USDTRY") {
        return Java.to([ usdBid, usdAsk ], "double[]");
    }

    /* 3) Diğer tüm *USD kurları (EURUSD, GBPUSD, JPYUSD …) */
    var camel = calcName.substring(0,1).toUpperCase() +
                calcName.substring(1).toLowerCase();            // EURUSD→Eurusd
    var bid = avgBySuffix(context, camel + "Bid");
    var ask = avgBySuffix(context, camel + "Ask");

    return Java.to([ usdBid * bid, usdAsk * ask ], "double[]");
}
