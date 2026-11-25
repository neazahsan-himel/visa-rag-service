package com.neaz.travel.visa.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * One part of an official visa fee, e.g. the Government of India's basic visa
 * fee or the processing fee charged by the Indian Visa Application Centre.
 *
 * <p>A zero amount is meaningful, not missing data: "no government fee is
 * charged" is itself an official fact and is stored as a zero-amount
 * component. Amounts are normalized to the currency's minor-unit scale, so
 * {@code 800} and {@code 800.00} are equal; an amount with more precision than
 * the currency allows is rejected rather than silently rounded.
 *
 * @param type      which part of the fee this is
 * @param chargedBy who collects this component (e.g. "Indian Visa Application Centre")
 * @param amount    the amount, never negative
 * @param currency  the ISO 4217 currency this component is charged in
 */
public record FeeComponent(Type type, String chargedBy, BigDecimal amount, Currency currency) {

    /**
     * Fee components as named by the official India visa fee policy: basic
     * fee, special fee, and processing fee charged by the out-sourcing agency.
     */
    public enum Type {
        BASIC,
        SPECIAL,
        PROCESSING
    }

    public FeeComponent {
        Objects.requireNonNull(type, "type must not be null");
        if (chargedBy == null || chargedBy.isBlank()) {
            throw new IllegalArgumentException("chargedBy must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        amount = normalizeScale(amount, currency);
    }

    private static BigDecimal normalizeScale(BigDecimal amount, Currency currency) {
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            // Pseudo-currencies (e.g. XXX) have no defined minor unit.
            return amount;
        }
        try {
            return amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "amount " + amount + " has more decimal places than " + currency.getCurrencyCode() + " allows", e);
        }
    }
}
