package com.lpscanner.app

import android.app.Application
import com.lpscanner.app.database.PlateRepository
import com.lpscanner.app.database.PlateSheetSetup
import com.lpscanner.app.database.SelectedSheetStore
import com.lpscanner.app.google.DriveApi
import com.lpscanner.app.google.GoogleApiClient
import com.lpscanner.app.google.GoogleAuth
import com.lpscanner.app.google.GoogleSession
import com.lpscanner.app.google.SheetsApi

class PlateScannerApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** App-wide singletons, wired by hand (small app, no DI framework). */
class AppContainer(app: Application) {
    val googleAuth = GoogleAuth(app)
    val googleSession = GoogleSession(googleAuth)
    private val apiClient = GoogleApiClient(googleSession)
    val drive = DriveApi(apiClient)
    val sheets = SheetsApi(apiClient)
    val sheetSetup = PlateSheetSetup(sheets)
    val selectedSheet = SelectedSheetStore(app)
    val plates = PlateRepository(sheets)
}
