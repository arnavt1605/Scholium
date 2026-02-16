package com.example.scholium.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun RegionSelectionDialog(
    croppedBitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Selection") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 1. Show the cropped image
                Image(
                    bitmap = croppedBitmap.asImageBitmap(),
                    contentDescription = "Crop Preview",
                    modifier = Modifier.height(150.dp).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Buttons for Type
                Text("Content Type:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onConfirm("Text") }) { Text("Text") }
                    FilledTonalButton(onClick = { onConfirm("Figure") }) { Text("Fig") }
                    FilledTonalButton(onClick = { onConfirm("Table") }) { Text("Table") }
                }
            }
        },
        confirmButton = {}, // We handled buttons above
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}