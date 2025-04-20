// myFormula.js

function compute(context) {
    var calcName = context.get("calcName"); 
    // USD/TRY => calcName="USDTRY"
    // EUR/TRY => calcName="EURTRY"
    // GBP/TRY => calcName="GBPTRY"

    // Ortak PF1,PF2 => (pf1Bid, pf1Ask, pf2Bid, pf2Ask) genelde USDTRY parametreleri
    var pf1Bid = context.get("pf1Bid") || 0.0;
    var pf1Ask = context.get("pf1Ask") || 0.0;
    var pf2Bid = context.get("pf2Bid") || 0.0;
    var pf2Ask = context.get("pf2Ask") || 0.0;

    if (calcName === "USDTRY") {
        // Doküman: USDTRY.bid => (PF1_USDTRY.bid + PF2_USDTRY.bid)/2
        //          USDTRY.ask => (PF1_USDTRY.ask + PF2_USDTRY.ask)/2
        var bid = (pf1Bid + pf2Bid) / 2.0;
        var ask = (pf1Ask + pf2Ask) / 2.0;
        return Java.to([ bid, ask ], "double[]");

    } else if (calcName === "EURTRY") {
        // Doküman: 
        //   usdMid = ((pf1UsdTry.bid+pf2UsdTry.bid)/2 + (pf1UsdTry.ask+pf2UsdTry.ask)/2)/2
        //   EURTRY.bid = usdMid * ((PF1_EURUSD.bid+PF2_EURUSD.bid)/2)
        //   EURTRY.ask = usdMid * ((PF1_EURUSD.ask+PF2_EURUSD.ask)/2)

        // 1) USD mid
        var avgBid = (pf1Bid + pf2Bid)/2.0;
        var avgAsk = (pf1Ask + pf2Ask)/2.0;
        var usdMid = (avgBid + avgAsk)/2.0;

        // 2) EURUSD parametreleri => pf1EurUsdBid, pf2EurUsdBid, pf1EurUsdAsk, pf2EurUsdAsk
        var pf1EurUsdBid = context.get("pf1EurUsdBid") || 0.0;
        var pf2EurUsdBid = context.get("pf2EurUsdBid") || 0.0;
        var pf1EurUsdAsk = context.get("pf1EurUsdAsk") || 0.0;
        var pf2EurUsdAsk = context.get("pf2EurUsdAsk") || 0.0;

        var eurAvgBid = (pf1EurUsdBid + pf2EurUsdBid)/2.0;
        var eurAvgAsk = (pf1EurUsdAsk + pf2EurUsdAsk)/2.0;

        var eurBid = usdMid * eurAvgBid;
        var eurAsk = usdMid * eurAvgAsk;
        return Java.to([ eurBid, eurAsk ], "double[]");

    } else if (calcName === "GBPTRY") {
        // Doküman: 
        //   usdMid = ((pf1UsdTry.bid+pf2UsdTry.bid)/2 + (pf1UsdTry.ask+pf2UsdTry.ask)/2)/2
        //   GBPTRY.bid = usdMid * ((pf1GbpUsd.bid+pf2GbpUsd.bid)/2)
        //   GBPTRY.ask = usdMid * ((pf1GbpUsd.ask+pf2GbpUsd.ask)/2)

        var avgBid2 = (pf1Bid + pf2Bid)/2.0;
        var avgAsk2 = (pf1Ask + pf2Ask)/2.0;
        var usdMid2 = (avgBid2 + avgAsk2)/2.0;

        // Parametreler => pf1GbpUsdBid, pf2GbpUsdBid, pf1GbpUsdAsk, pf2GbpUsdAsk
        var pf1GbpUsdBid = context.get("pf1GbpUsdBid") || 0.0;
        var pf2GbpUsdBid = context.get("pf2GbpUsdBid") || 0.0;
        var pf1GbpUsdAsk = context.get("pf1GbpUsdAsk") || 0.0;
        var pf2GbpUsdAsk = context.get("pf2GbpUsdAsk") || 0.0;

        var gbpAvgBid = (pf1GbpUsdBid + pf2GbpUsdBid)/2.0;
        var gbpAvgAsk = (pf1GbpUsdAsk + pf2GbpUsdAsk)/2.0;

        var gbpBid = usdMid2 * gbpAvgBid;
        var gbpAsk = usdMid2 * gbpAvgAsk;
        return Java.to([ gbpBid, gbpAsk ], "double[]");

    } else {
        throw "Unknown calcName => " + calcName;
    }
}
