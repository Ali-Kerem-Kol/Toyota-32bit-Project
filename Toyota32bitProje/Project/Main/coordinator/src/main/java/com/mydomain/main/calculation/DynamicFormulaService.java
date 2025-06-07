package com.mydomain.main.calculation;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.exception.FormulaEngineException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.FileReader;
import java.util.Map;

/**
 * {@code DynamicFormulaService}, dışarıdan sağlanan bir JavaScript dosyasını yükleyerek
 * `compute(context)` fonksiyonunu çalıştırır ve hesaplama sonuçlarını (bid/ask değerleri)
 * bir `double[]` olarak döndürür. Bu sınıf, dinamik kur hesaplama işlemini desteklemek
 * için Nashorn veya benzeri bir JavaScript motoru kullanır.
 *
 * <p>Hizmetin temel işleyişi şu adımları içerir:
 * <ul>
 *   <li>JavaScript motorunun yalnızca "javascript" yöntemiyle çalıştırılması kontrol edilir.</li>
 *   <li>Konfigürasyon dosyasından (ConfigReader) belirtilen JavaScript dosyası yolu yüklenir.</li>
 *   <li>Script bir kez initialize edilir ve `compute` fonksiyonu context verileriyle çağrılır.</li>
 *   <li>Hata durumlarında detaylı loglama yapılır ve istisnalar fırlatılır.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Statik bir servis olarak tasarlanmıştır; motor yalnızca ilk çağrıda initialize edilir.</li>
 *   <li>Loglama için Apache Log4j kullanılır ve hata ayıklama için izleme (trace) seviyesi desteklenir.</li>
 *   <li>Dış bağımlılıklar (örn. Redis/Kafka) yoktur; yalnızca dosya sistemi ve JVM motoru kullanılır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class DynamicFormulaService {

    private static final Logger log = LogManager.getLogger(DynamicFormulaService.class);

    private static ScriptEngine scriptEngine;
    private static boolean initialized = false;

    private static final String CALCULATION_METHOD = ConfigReader.getCalculationMethod();
    private static final String FORMULA_FILE_PATH = ConfigReader.getFormulaFilePath();

    /**
     * `compute(context)` JavaScript fonksiyonunu çağırır ve hesaplanan bid/ask değerlerini
     * içeren bir `double[]` döndürür. Bu metod, ilk çağrıda JavaScript motorunu initialize
     * eder ve belirtilen dosya yolundan (FORMULA_FILE_PATH) JavaScript kodunu yükler.
     * Yalnızca "javascript" yöntemi desteklenir; aksi halde istisna fırlatılır.
     *
     * <p>İşlem adımları:
     * <ol>
     *   <li>Hesaplama yöntemi kontrol edilir ("javascript" olmalı).</li>
     *   <li>Script motoru (Nashorn) initialize edilmemişse yüklenir.</li>
     *   <li>`compute` fonksiyonu context verileriyle çağrılır ve sonuç dönülür.</li>
     *   <li>Hata durumunda detaylı loglama yapılır ve istisna fırlatılır.</li>
     * </ol>
     * </p>
     *
     * @param context Hesaplama için gerekli değişkenleri içeren map (örn. {"calcName": "EURUSD",
     *                "TCP_PLATFORMUsdtryBid": 32.5}), null olmamalı
     * @return JavaScript `compute` fonksiyonundan dönen [bid, ask] değerlerini içeren `double[]`
     * @throws FormulaEngineException Eğer hesaplama yöntemi desteklenmezse, motor initialize
     *                                edilemezse, dosya yüklenemezse veya fonksiyon hata verirse
     * @throws IllegalArgumentException Eğer context null ise
     */
    public static double[] calculate(Map<String, Object> context) throws FormulaEngineException {
        if (!"javascript".equalsIgnoreCase(CALCULATION_METHOD)) {
            String msg = "Unsupported calculation method: " + CALCULATION_METHOD;
            log.debug(msg);
            throw new FormulaEngineException(msg);
        }

        try {
            if (!initialized) {
                log.trace("Initializing JavaScript engine and loading script...");

                ScriptEngineManager manager = new ScriptEngineManager();
                scriptEngine = manager.getEngineByName("JavaScript");

                if (scriptEngine == null) {
                    String err = "JavaScript engine not found in JVM.";
                    log.debug(err);
                    throw new FormulaEngineException(err);
                }

                scriptEngine.eval(new FileReader(FORMULA_FILE_PATH));
                log.info("✅ JavaScript formula loaded from: {}", FORMULA_FILE_PATH);
                initialized = true;
            }

            Invocable invocable = (Invocable) scriptEngine;
            log.trace("Invoking JavaScript function: compute(context) with keys: {}", context.keySet());

            Object result = invocable.invokeFunction("compute", context);

            if (!(result instanceof double[])) {
                String err = "JavaScript function 'compute' must return double[] but got: " + result;
                log.debug("❌ {}", err);
                throw new FormulaEngineException(err);
            }

            double[] output = (double[]) result;
            log.trace("JavaScript compute() result: bid={}, ask={}", output[0], output[1]);
            return output;

        } catch (Exception e) {
            String msg = "JavaScript formula execution failed: " + e.getMessage();
            log.debug("❌ {}", msg);
            throw new FormulaEngineException(msg);
        }
    }
}
