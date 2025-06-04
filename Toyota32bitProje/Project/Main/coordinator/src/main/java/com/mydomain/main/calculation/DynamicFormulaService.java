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
 * DynamicFormulaService dışarıdan sağlanan JavaScript dosyasını
 * yükleyip compute(context) fonksiyonunu çalıştırarak
 * hesaplama sonuçlarını döndüren servistir.
 */
public class DynamicFormulaService {

    private static final Logger logger = LogManager.getLogger(DynamicFormulaService.class);

    private static ScriptEngine scriptEngine;
    private static boolean initialized = false;

    private static final String CALCULATION_METHOD = ConfigReader.getCalculationMethod();
    private static final String FORMULA_FILE_PATH = ConfigReader.getFormulaFilePath();

    /**
     * compute(context) JavaScript fonksiyonunu çağırır ve
     * hesaplanan bid/ask değerlerini içeren double[] döner.
     *
     * @param context Hesaplama için gerekli değişkenleri içeren map
     * @return JavaScript fonksiyonundan dönen bid ve ask değerleri
     * @throws FormulaEngineException JavaScript motoru ya da hesaplama hatası durumunda
     */
    public static double[] calculate(Map<String, Object> context) throws FormulaEngineException {
        if (!"javascript".equalsIgnoreCase(CALCULATION_METHOD)) {
            String msg = "Unsupported calculation method: " + CALCULATION_METHOD;
            logger.error(msg);
            throw new FormulaEngineException(msg);
        }

        try {
            if (!initialized) {
                logger.trace("Initializing JavaScript engine and loading script...");

                ScriptEngineManager manager = new ScriptEngineManager();
                scriptEngine = manager.getEngineByName("JavaScript");

                if (scriptEngine == null) {
                    String err = "JavaScript engine not found in JVM.";
                    logger.debug(err);
                    throw new FormulaEngineException(err);
                }

                scriptEngine.eval(new FileReader(FORMULA_FILE_PATH));
                logger.info("✅ JavaScript formula loaded from: {}", FORMULA_FILE_PATH);
                initialized = true;
            }

            Invocable invocable = (Invocable) scriptEngine;
            logger.trace("Invoking JavaScript function: compute(context) with keys: {}", context.keySet());

            Object result = invocable.invokeFunction("compute", context);

            if (!(result instanceof double[])) {
                String err = "JavaScript function 'compute' must return double[] but got: " + result;
                logger.debug("❌ {}", err);
                throw new FormulaEngineException(err);
            }

            double[] output = (double[]) result;
            logger.trace("JavaScript compute() result: bid={}, ask={}", output[0], output[1]);
            return output;

        } catch (Exception e) {
            String msg = "JavaScript formula execution failed: " + e.getMessage();
            logger.debug("❌ {}", msg);
            throw new FormulaEngineException(msg);
        }
    }
}
