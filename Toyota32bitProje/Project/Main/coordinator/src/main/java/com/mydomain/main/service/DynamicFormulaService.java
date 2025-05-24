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

    private static ScriptEngine SCRPIT_ENGINE;
    private static boolean INITIALIZED = false;

    private static final String CALCULATION_METHOD = ConfigReader.getCalculationMethod();
    private static final String FORMULA_FILE_PATH = ConfigReader.getFormulaFilePath();

    /**
     * compute(context) JavaScript fonksiyonunu çağırır ve
     * hesaplanan bid/ask değerlerini içeren double[] döner.
     *
     * @param context Hesaplama için gerekli değişkenleri içeren map
     * @return JavaScript fonksiyonundan dönen bid ve ask değerleri
     */
    public static double[] calculate(Map<String, Object> context) {
        //String method = ConfigReader.getCalculationMethod();
        //String filePath = ConfigReader.getFormulaFilePath();

        if (!"javascript".equalsIgnoreCase(CALCULATION_METHOD)) {
            String msg = "Only 'javascript' is supported. Found: " + CALCULATION_METHOD;
            logger.error(msg);
            throw new RuntimeException(msg);
        }

        try {
            if (!INITIALIZED) {
                ScriptEngineManager mgr = new ScriptEngineManager();
                SCRPIT_ENGINE = mgr.getEngineByName("JavaScript");
                if (SCRPIT_ENGINE == null) throw new RuntimeException("JavaScript engine not found!");
                SCRPIT_ENGINE.eval(new FileReader(FORMULA_FILE_PATH));
                logger.info("Loaded JavaScript formula from: {}", FORMULA_FILE_PATH);
                INITIALIZED = true;
            }

            Invocable invocable = (Invocable) SCRPIT_ENGINE;
            Object result = invocable.invokeFunction("compute", context);
            if (!(result instanceof double[])) throw new RuntimeException("Script 'compute' must return double[]!");
            return (double[]) result;

        } catch (Exception e) {
            logger.error("Script error => {}", e.getMessage(), e);
            throw new RuntimeException("Dynamic formula error: " + e.getMessage(), e);
        }
    }

}