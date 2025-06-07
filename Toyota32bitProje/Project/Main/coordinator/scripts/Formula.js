// Formula.js - Kurları hesaplamak için ES5 uyumlu JavaScript fonksiyonu
// Bu dosya, DynamicFormulaService tarafından çağrılır ve bid/ask değerlerini döndürür.

/* =========================================================================
   YARDIMCI FONKSİYONLAR
   ========================================================================= */

/**
 * Bir string'in belirli bir sonek ile bitip bitmediğini kontrol eder.
 * ES5'te native endsWith fonksiyonu olmadığından manuel implementasyon kullanılır.
 * @param {string} inputString - Kontrol edilecek string
 * @param {string} suffix - Aranan sonek
 * @returns {boolean} - String sonek ile bitiyorsa true, aksi halde false
 */
function endsWith(inputString, suffix) {
    return inputString.indexOf(suffix, inputString.length - suffix.length) !== -1;
}

/**
 * Context'te anahtarı verilen sonek ile biten değerlerin ortalamasını hesaplar.
 * Minimum veri sayısına ulaşılmazsa hata fırlatır.
 * @param {Object} context - Veri içeren context map
 * @param {string} keySuffix - Anahtar soneki (örn. "UsdtryBid")
 * @param {number} minimumCount - Minimum gerekli veri sayısı
 * @returns {number} - Hesaplanan ortalama değer
 * @throws {string} - Yetersiz veri varsa hata mesajı
 */
function calculateAverageBySuffix(context, keySuffix, minimumCount) {
    var keyIterator = context.keySet().iterator();
    var totalSum = 0.0;
    var dataCount = 0;

    while (keyIterator.hasNext()) {
        var currentKey = String(keyIterator.next());
        if (endsWith(currentKey, keySuffix)) {
            var currentValue = context.get(currentKey);
            if (currentValue != null) {
                totalSum += currentValue;
                dataCount++;
            }
        }
    }

    if (dataCount < minimumCount) {
        throw "Insufficient data: at least " + minimumCount + " sources required for '" + keySuffix + "' (available: " + dataCount + ")";
    }

    return totalSum / dataCount;
}

/* =========================================================================
   ANA HESAPLAMA FONKSİYONU
   ========================================================================= */

/**
 * Context'teki verilere göre kurları hesaplar ve bid/ask değerlerini döndürür.
 * USDTRY için direkt ortalama, diğer kurlar için çapraz hesaplama yapar.
 * @param {Object} context - Hesaplama için gerekli veriler (currencyCode, bid/ask)
 * @returns {double[]} - [bid, ask] dizisi
 */
function compute(context) {
    // Hesaplanacak kur kodunu al (örn. "USDTRY", "EURUSD")
    var currencyCode = String(context.get("calcName"));

    // 1. USDTRY'nin bid ve ask ortalamalarını hesapla (her zaman gerekli)
    var usdTryBid = calculateAverageBySuffix(context, "UsdtryBid", 2);
    var usdTryAsk = calculateAverageBySuffix(context, "UsdtryAsk", 2);

    // 2. Eğer hesaplanacak kur USDTRY ise, direkt bu değerleri döndür
    if (currencyCode === "USDTRY") {
        return Java.to([usdTryBid, usdTryAsk], "double[]");
    }

    // 3. Diğer kurlar için (örn. EURUSD, DOGEUSD) çapraz hesaplama yap
    var currencyKey = currencyCode.substring(0, 1).toUpperCase() +
                     currencyCode.substring(1).toLowerCase(); // EURUSD → Eurusd
    var currencyBid = calculateAverageBySuffix(context, currencyKey + "Bid", 2);
    var currencyAsk = calculateAverageBySuffix(context, currencyKey + "Ask", 2);

    // Çapraz kur hesaplama: USDTRY * XXXUSD
    return Java.to([usdTryBid * currencyBid, usdTryAsk * currencyAsk], "double[]");
}