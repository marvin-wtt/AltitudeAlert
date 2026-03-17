package one.ballooning.altitudealert.ui

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
    val errorMessage: String? get() = (this as? Invalid)?.reason
}

object Validators {

    fun qnh(value: String): ValidationResult {
        if (value.isEmpty()) return ValidationResult.Invalid("Required")
        val v = value.toFloatOrNull() ?: return ValidationResult.Invalid("Must be a number")
        if (v < 800 || v > 1100) return ValidationResult.Invalid("Must be between 800 and 1100 hPa")
        return ValidationResult.Valid
    }

    fun altitudeFeet(value: String, label: String = "Altitude"): ValidationResult {
        if (value.isEmpty()) return ValidationResult.Invalid("Required")
        val v = value.toIntOrNull() ?: return ValidationResult.Invalid("Must be a whole number")
        if (v < 0) return ValidationResult.Invalid("$label must be positive")
        if (v > 99_999) return ValidationResult.Invalid("$label seems unrealistically high")
        return ValidationResult.Valid
    }

    fun bandLower(lowerValue: String, upperValue: String): ValidationResult {
        val base = altitudeFeet(lowerValue, "Lower limit")
        if (!base.isValid) return base
        val lower = lowerValue.toInt()
        val upper = upperValue.toIntOrNull() ?: return ValidationResult.Valid
        if (lower >= upper) return ValidationResult.Invalid("Must be below the upper limit")
        return ValidationResult.Valid
    }

    fun bandUpper(upperValue: String, lowerValue: String): ValidationResult {
        val base = altitudeFeet(upperValue, "Upper limit")
        if (!base.isValid) return base
        val upper = upperValue.toInt()
        val lower = lowerValue.toIntOrNull() ?: return ValidationResult.Valid
        if (upper <= lower) return ValidationResult.Invalid("Must be above the lower limit")
        return ValidationResult.Valid
    }

    fun approachThreshold(value: String): ValidationResult {
        if (value.isEmpty()) return ValidationResult.Invalid("Required")
        val v = value.toIntOrNull() ?: return ValidationResult.Invalid("Must be a whole number")
        if (v < 1) return ValidationResult.Invalid("Must be at least 1 ft")
        if (v > 9_999) return ValidationResult.Invalid("Must be 9,999 ft or less")
        return ValidationResult.Valid
    }
}