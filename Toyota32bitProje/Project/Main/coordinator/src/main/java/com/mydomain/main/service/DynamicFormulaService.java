package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
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

    /**
     * compute(context) JavaScript fonksiyonunu çağırır ve
     * hesaplanan bid/ask değerlerini içeren double[] döner.
     *
     * @param context Hesaplama için gerekli değişkenleri içeren map
     * @return JavaScript fonksiyonundan dönen bid ve ask değerleri
     */
    public static double[] calculate(Map<String, Object> context) {
        String method = ConfigReader.getCalculationMethod();
        String filePath = ConfigReader.getFormulaFilePath();

        if (!"javascript".equalsIgnoreCase(method)) {
            String msg = "Only 'javascript' is supported. Found: " + method;
            logger.error(msg);
            throw new RuntimeException(msg);
        }

        try {
            if (!initialized) {
                ScriptEngineManager mgr = new ScriptEngineManager();
                scriptEngine = mgr.getEngineByName("JavaScript");
                if (scriptEngine == null) {
                    throw new RuntimeException("JavaScript engine not found!");
                }
                scriptEngine.eval(new FileReader(filePath));
                logger.info("Loaded JavaScript formula from: {}", filePath);
                initialized = true;
            }

            Invocable invocable = (Invocable) scriptEngine;
            Object result = invocable.invokeFunction("compute", context);

            if (!(result instanceof double[])) {
                throw new RuntimeException("Script 'compute' must return double[]!");
            }
            return (double[]) result;

        } catch (Exception e) {
            logger.error("Script error => {}", e.getMessage(), e);
            throw new RuntimeException("Dynamic formula error: " + e.getMessage(), e);
        }
    }

}
