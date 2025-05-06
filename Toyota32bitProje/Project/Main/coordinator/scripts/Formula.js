// myFormula.js

/**
 * Java’dan gelen context’e göre:
 * - calcName: "USDTRY", "EURUSD" veya "GBPUSD"
 * - pf1UsdtryBid, pf1UsdtryAsk, pf2UsdtryBid, pf2UsdtryAsk
 * - pf1EurusdBid, pf1EurusdAsk, pf2EurusdBid, pf2EurusdAsk
 * - pf1GbpusdBid, pf1GbpusdAsk, pf2GbpusdBid, pf2GbpusdAsk
 * anahtarlarıyla çalışır. Cross‐rate hesaplamalarında bid ve ask’i
 * ayrı ayrı koruyacak şekilde düzenlenmiştir.
 */
function compute(context) {
    var calcName = context.get("calcName");

    if (calcName === "USDTRY") {
        // 1) Direct USDTRY: pf1 + pf2 ortalaması
        var usdBid = (context.get("pf1UsdtryBid") + context.get("pf2UsdtryBid")) / 2;
        var usdAsk = (context.get("pf1UsdtryAsk") + context.get("pf2UsdtryAsk")) / 2;
        return Java.to([ usdBid, usdAsk ], "double[]");

    } else if (calcName === "EURUSD") {
        // 2) Cross EURTRY
        //    Bid_try = Bid_usdtry * Bid_eurusd
        //    Ask_try = Ask_usdtry * Ask_eurusd

        // USDTRY bid/ask
        var usdBid = (context.get("pf1UsdtryBid") + context.get("pf2UsdtryBid")) / 2;
        var usdAsk = (context.get("pf1UsdtryAsk") + context.get("pf2UsdtryAsk")) / 2;

        // EURUSD bid/ask
        var eurBid = (context.get("pf1EurusdBid") + context.get("pf2EurusdBid")) / 2;
        var eurAsk = (context.get("pf1EurusdAsk") + context.get("pf2EurusdAsk")) / 2;

        // Çarpımla cross‐rate
        var eurTryBid = usdBid * eurBid;
        var eurTryAsk = usdAsk * eurAsk;
        return Java.to([ eurTryBid, eurTryAsk ], "double[]");

    } else if (calcName === "GBPUSD") {
        // 3) Cross GBPTRY
        //    Bid_try = Bid_usdtry * Bid_gbpusd
        //    Ask_try = Ask_usdtry * Ask_gbpusd

        // USDTRY bid/ask
        var usdBid = (context.get("pf1UsdtryBid") + context.get("pf2UsdtryBid")) / 2;
        var usdAsk = (context.get("pf1UsdtryAsk") + context.get("pf2UsdtryAsk")) / 2;

        // GBPUSD bid/ask
        var gbpBid = (context.get("pf1GbpusdBid") + context.get("pf2GbpusdBid")) / 2;
        var gbpAsk = (context.get("pf1GbpusdAsk") + context.get("pf2GbpusdAsk")) / 2;

        // Çarpımla cross‐rate
        var gbpTryBid = usdBid * gbpBid;
        var gbpTryAsk = usdAsk * gbpAsk;
        return Java.to([ gbpTryBid, gbpTryAsk ], "double[]");

    } else {
        // Tanım dışı calcName geldiğinde hata fırlat
        throw "Unknown calcName => " + calcName;
    }
}
