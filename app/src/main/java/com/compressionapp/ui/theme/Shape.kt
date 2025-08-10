package com.compressionapp.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    // A more expressive shape for small components like buttons
    small = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),

    // A standard rounded shape for medium components like cards
    medium = RoundedCornerShape(16.dp),

    // A larger, softer rounded shape for large components like bottom sheets
    large = RoundedCornerShape(24.dp)
)
