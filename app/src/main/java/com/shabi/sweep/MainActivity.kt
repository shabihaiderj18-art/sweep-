package com.shabi.sweep

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * true  = photos go to the phone's bin (recoverable for about 30 days).
 * false = photos are deleted permanently and space is freed right away.
 */
const val USE_TRASH = true

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SweepTheme { SweepApp() } }
    }
}

@Composable
fun SweepApp(vm: SweepViewModel = viewModel()) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionChecked() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> vm.onDeleteResult(result.resultCode == Activity.RESULT_OK) }

    // Re-check access every time the app comes back to the front (e.g. after changing settings).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onPermissionChecked() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (!vm.hasPermission) {
                PermissionScreen(
                    onAllow = { permissionLauncher.launch(PhotoPermissions.required()) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    },
                )
            } else when (vm.screen) {
                Screen.Albums -> AlbumsScreen(vm)
                Screen.Months -> MonthsScreen(vm)
                Screen.Swipe -> SwipeScreen(vm)
                Screen.Review -> ReviewScreen(vm) { uris ->
                    // Android shows its own "Allow Sweep to delete N photos?" dialog.
                    val request = if (USE_TRASH) {
                        MediaStore.createTrashRequest(context.contentResolver, uris, true)
                    } else {
                        MediaStore.createDeleteRequest(context.contentResolver, uris)
                    }
                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Swipe left to delete.\nSwipe right to keep.",
            fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Sweep shows your photos and videos one at a time, month by month. Nothing is deleted until you review your choices and confirm.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp, lineHeight = 23.sp,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAllow) { Text("Allow access") }
        TextButton(onClick = onOpenSettings) { Text("Open app settings") }
    }
}
