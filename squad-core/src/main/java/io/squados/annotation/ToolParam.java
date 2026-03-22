package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Describes a parameter of a {@literal @}SquadTool method.
 * The description is included in the JSON schema sent to the LLM.
 *
 * Usage:
 * <pre>
 * {@literal @}SquadTool(name = "getStockPrice", description = "...")
 * public String getStockPrice(
 *     {@literal @}ToolParam(description = "Ticker symbol e.g. AAPL") String ticker,
 *     {@literal @}ToolParam(description = "Currency e.g. USD", required = false) String currency
 * ) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Documented
public @interface ToolParam {
    String description() default "";
    boolean required() default true;
}